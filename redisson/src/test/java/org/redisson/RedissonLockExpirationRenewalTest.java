package org.redisson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
     * <p>
     * Matches the issue reproducer: continuous interrupts (no sleep between
     * them), several workers, multi-second storm. Also cancels in-flight
     * {@code tryLockAsync} futures so the cancel-handler path is exercised
     * without relying on a single narrow race window.
     */
    @Test
    public void testWatchdogDoesNotOutliveInterruptedAcquire() throws Exception {
        String name = "test:watchdog:interrupt-leak";
        redisson.getKeys().delete(name);

        AtomicBoolean stop = new AtomicBoolean();
        int workers = 4;
        Thread[] threads = new Thread[workers];
        for (int w = 0; w < workers; w++) {
            threads[w] = new Thread(() -> {
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
            }, "watchdog-interrupt-worker-" + w);
            threads[w].start();
        }

        // Continuous interrupt storm (issue #7272 reproducer used ~20s busy loop).
        long until = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < until) {
            for (Thread t : threads) {
                t.interrupt();
            }
        }
        stop.set(true);
        for (Thread t : threads) {
            t.interrupt();
            t.join(10_000);
            if (t.isAlive()) {
                t.interrupt();
                t.join(5_000);
            }
        }

        // Longer than watchdog TTL: a released key must be gone; a renewed orphan stays.
        Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 3);
        long ttl = redisson.getMap(name).remainTimeToLive();
        boolean stillLocked = redisson.getLock(name).isLocked();
        redisson.getKeys().delete(name);

        assertThat(stillLocked)
                .as("orphan lock still held/renewed after interrupted acquire/unlock")
                .isFalse();
        assertThat(ttl)
                .as("orphan lock key still present with ttl=" + ttl + "ms after workers stopped")
                .isLessThanOrEqualTo(0L);
    }

    /**
     * Cancel of an in-flight {@code tryLockAsync} must drop speculative watchdog
     * registration and unlock if SET already succeeded. Without the cancel handler
     * on the outer future (#7272), cancelling the acquire leaves the key held and
     * renewed forever — the outer future is cancelled but the inner SET/schedule
     * still runs.
     * <p>
     * Important: do not call {@code forceUnlock()} after a successful cancel — that
     * would mask the leak by cancelling renewal itself.
     */
    @Test
    public void testCancelledTryLockAsyncDoesNotLeaveWatchdog() throws Exception {
        String name = "test:watchdog:cancel-async";
        redisson.getKeys().delete(name);
        RLock lock = redisson.getLock(name);

        int cancelledInFlight = 0;
        for (int i = 0; i < 400; i++) {
            RFuture<Boolean> future = lock.tryLockAsync();
            // Yield once so the acquire is dispatched, then cancel the outer future.
            Thread.yield();
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                cancelledInFlight++;
                // Production fix must cancelExpirationRenewal + unlockAsync.
                // Do not forceUnlock — that would hide an orphaned watchdog.
            } else {
                // Already completed: normal unlock (not the cancel race under test).
                try {
                    Boolean acquired = future.toCompletableFuture().join();
                    if (Boolean.TRUE.equals(acquired)) {
                        lock.unlock();
                    }
                } catch (RuntimeException ignored) {
                    // acquire failed
                }
            }
        }

        assertThat(cancelledInFlight)
                .as("expected some in-flight cancels to exercise the race")
                .isGreaterThan(0);

        // Allow cancel handlers / unlockAsync to settle, then wait past watchdog TTL.
        Thread.sleep(500);
        Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 3);

        long ttl = redisson.getMap(name).remainTimeToLive();
        boolean stillLocked = lock.isLocked();
        redisson.getKeys().delete(name);

        assertThat(stillLocked)
                .as("cancelled tryLockAsync left lock held (watchdog still renewing)")
                .isFalse();
        assertThat(ttl)
                .as("tryLockAsync cancel left a watchdog-renewed orphan key ttl=" + ttl)
                .isLessThanOrEqualTo(0L);
    }

}
