package org.redisson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonLockExpirationRenewalTest extends RedisDockerTest {

    private static final String LOCK_KEY = "LOCK_KEY";
    public static final long LOCK_WATCHDOG_TIMEOUT = 1_000L;

    RedissonClient redisson;
    GenericContainer<?> redis;

    @BeforeEach
    public void beforeEachTest() {
        redis = createContainer();
        redis.start();

        Config c = createConfig(redis);
        c.setLockWatchdogTimeout(LOCK_WATCHDOG_TIMEOUT);
        c.setLockWatchdogBatchSize(50);
        redisson = Redisson.create(c);
    }

    @AfterEach
    public void afterEachTest() {
        redisson.shutdown();
        redis.stop();
    }

    @Test
    public void testWriteLockAfterTimeout() throws InterruptedException {
        RReadWriteLock rw = redisson.getReadWriteLock(LOCK_KEY);
        RLock lock = rw.writeLock();
        lock.lock();
        try {
            // force expiration renewal error
            restart(redis);
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            assertThatThrownBy(() -> {
                lock.unlock();
            }).isInstanceOf(IllegalMonitorStateException.class);
        }

        RReadWriteLock lock2 = redisson.getReadWriteLock(LOCK_KEY);
        lock2.writeLock().lock();
        try {
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            lock2.writeLock().unlock();
        }

        Thread.sleep(1000);

        lock2.writeLock().lock();
        try {
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            lock2.writeLock().unlock();
        }
    }

    @Test
    public void testReadLockAfterTimeout() throws InterruptedException {
        RReadWriteLock rw = redisson.getReadWriteLock(LOCK_KEY);
        RLock lock = rw.readLock();
        lock.lock();
        try {
            // force expiration renewal error
            restart(redis);
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            assertThatThrownBy(() -> {
                lock.unlock();
            }).isInstanceOf(IllegalMonitorStateException.class);
        }

        RReadWriteLock lock2 = redisson.getReadWriteLock(LOCK_KEY);
        lock2.readLock().lock();
        try {
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            lock2.readLock().unlock();
        }
    }

    @Test
    public void testLockAfterTimeout() throws InterruptedException {
        RLock lock = redisson.getLock(LOCK_KEY);
        lock.lock();
        try {
            // force expiration renewal error
            restart(redis);
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            assertThatThrownBy(lock::unlock).isInstanceOf(IllegalMonitorStateException.class);
        }

        RLock lock2 = redisson.getLock(LOCK_KEY);
        lock2.lock();
        try {
            // wait for timeout
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
        } finally {
            lock2.unlock();
        }
    }
    
    @Test
    public void testLockReentrantRenew() throws InterruptedException {
        RLock lock = redisson.getLock(LOCK_KEY);
        lock.lock();
        lock.lock();
        lock.unlock();
        lock.unlock();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(()->{
            RedissonLock lock2 = (RedissonLock) redisson.getLock(LOCK_KEY);
            lock2.lock();
            while (true) {
                // doSomething
            }
        });
        
        try {
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 10);
        } finally {
            // lock2 should hold the lock
            RMap<Object, Object> map = redisson.getMap(LOCK_KEY);
            assertThat(map.isExists()).isTrue();

            executor.shutdownNow();
        }
    }

    /**
     * Watchdog must not outlive unlock when acquire is interrupted mid-flight
     * and release runs in finally (cancel vs schedule race). See #7272.
     */
    @Test
    public void testWatchdogDoesNotOutliveInterruptedAcquire() throws Exception {
        String name = "test:watchdog:interrupt-leak";
        redisson.getKeys().delete(name);

        AtomicBoolean stop = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            RLock lock = redisson.getLock(name);
            while (!stop.get()) {
                Thread.interrupted(); // clear so the next interrupt lands during acquire
                try {
                    lock.lockInterruptibly();
                } catch (InterruptedException | RuntimeException e) {
                    // acquire interrupted (may be wrapped as RedisException)
                } finally {
                    Thread.interrupted(); // allow unlock to complete without immediate re-interrupt
                    try {
                        lock.unlock();
                    } catch (RuntimeException ignored) {
                        // not held
                    }
                }
            }
        });
        worker.start();

        long until = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < until) {
            worker.interrupt();
            Thread.sleep(2);
        }
        stop.set(true);
        worker.join(10_000);
        if (worker.isAlive()) {
            worker.interrupt();
            worker.join(5_000);
        }

        Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 3);
        long ttl = redisson.getMap(name).remainTimeToLive();
        redisson.getKeys().delete(name);

        assertThat(ttl).as("orphan lock still renewed by watchdog after interrupted acquire/unlock").isLessThanOrEqualTo(0L);
    }

}
