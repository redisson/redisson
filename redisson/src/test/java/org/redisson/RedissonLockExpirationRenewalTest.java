package org.redisson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonLockExpirationRenewalTest extends RedisDockerTest {

    private static final String LOCK_KEY = "LOCK_KEY";
    public static final long LOCK_WATCHDOG_TIMEOUT = 1_000L;

    RedissonClient redisson;
    GenericContainer<?> redis;

    @BeforeEach
    public void beforeEachTest() {
        redis = createRedis();
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

}
