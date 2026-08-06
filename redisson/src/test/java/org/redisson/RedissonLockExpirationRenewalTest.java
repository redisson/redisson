package org.redisson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.LockRenewalFailureListener;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.NameMapper;
import org.testcontainers.containers.GenericContainer;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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
    public void testLockRenewalFailureListener() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> lockName = new AtomicReference<>();
        AtomicLong threadId = new AtomicLong();
        AtomicReference<Throwable> cause = new AtomicReference<>();
        recreateClient((name, id, error) -> {
            lockName.set(name);
            threadId.set(id);
            cause.set(error);
            latch.countDown();
        });

        long expectedThreadId = Thread.currentThread().getId();
        causeLockRenewalFailure(LOCK_KEY);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(lockName.get()).isEqualTo(LOCK_KEY);
        assertThat(threadId.get()).isEqualTo(expectedThreadId);
        assertThat(cause.get()).isNotNull();
    }

    @Test
    public void testLockRenewalFailureListenerIsNotCalledOnSuccessfulRenewal() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        recreateClient((name, id, error) -> calls.incrementAndGet());

        RLock lock = redisson.getLock(LOCK_KEY);
        lock.lock();
        try {
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
            assertThat(calls).hasValue(0);
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testLockRenewalFailureListenerExceptionDoesNotStopScheduling() {
        AtomicInteger calls = new AtomicInteger();
        recreateClient((name, id, error) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("listener failure");
        });

        causeLockRenewalFailure(LOCK_KEY);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(calls.get()).isGreaterThanOrEqualTo(2));
    }

    @Test
    public void testLockRenewalFailureListenerNameMapperExceptionDoesNotStopScheduling() {
        AtomicInteger unmapCalls = new AtomicInteger();
        redisson.shutdown();

        Config c = createConfig(redis);
        c.setLockWatchdogTimeout(LOCK_WATCHDOG_TIMEOUT);
        c.setLockWatchdogBatchSize(50);
        c.setLockRenewalFailureListener((name, id, error) -> {
            // empty
        });
        c.setNameMapper(new NameMapper() {
            @Override
            public String map(String name) {
                return name;
            }

            @Override
            public String unmap(String name) {
                unmapCalls.incrementAndGet();
                throw new IllegalStateException("name mapper failure");
            }
        });
        redisson = Redisson.create(c);

        causeLockRenewalFailure(LOCK_KEY);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(unmapCalls.get()).isGreaterThanOrEqualTo(2));
    }

    @Test
    public void testLockRenewalFailureListenerIsCalledOnlyForFailedBatch() throws InterruptedException {
        String healthyLockName = "healthy-lock";
        String failedLockName = "failed-lock";
        Set<String> notifiedLocks = ConcurrentHashMap.newKeySet();
        CountDownLatch failedLockNotification = new CountDownLatch(1);
        recreateClient((name, id, error) -> {
            notifiedLocks.add(name);
            if (failedLockName.equals(name)) {
                failedLockNotification.countDown();
            }
        }, 1);

        RLock healthyLock = redisson.getLock(healthyLockName);
        healthyLock.lock();
        causeLockRenewalFailure(failedLockName);

        assertThat(failedLockNotification.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(LOCK_WATCHDOG_TIMEOUT / 2);

        assertThat(notifiedLocks).containsExactly(failedLockName);
    }

    @Test
    public void testSlowLockRenewalFailureListenerDoesNotDelayScheduling() throws InterruptedException {
        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch secondCallStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        recreateClient((name, id, error) -> {
            if (calls.incrementAndGet() == 1) {
                firstCallStarted.countDown();
                try {
                    releaseFirstCall.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            secondCallStarted.countDown();
        });

        causeLockRenewalFailure(LOCK_KEY);

        try {
            assertThat(firstCallStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondCallStarted.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirstCall.countDown();
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

    private void recreateClient(LockRenewalFailureListener listener) {
        recreateClient(listener, 50);
    }

    private void causeLockRenewalFailure(String lockName) {
        RLock lock = redisson.getLock(lockName);
        lock.lock();
        redisson.getBucket(lockName).set("not a lock");
    }

    private void recreateClient(LockRenewalFailureListener listener, int batchSize) {
        redisson.shutdown();

        Config c = createConfig(redis);
        c.setLockWatchdogTimeout(LOCK_WATCHDOG_TIMEOUT);
        c.setLockWatchdogBatchSize(batchSize);
        c.setLockRenewalFailureListener(listener);
        redisson = Redisson.create(c);
    }

}
