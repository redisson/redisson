package org.redisson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.LockRenewalFailureListener;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        redisson = createClient(50);
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
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicReference<Throwable> cause = new AtomicReference<>();
        LockRenewalFailureListener listener = (name, error) -> {
            threadName.set(name);
            cause.set(error);
            latch.countDown();
        };

        RLock lock = redisson.getLock(LOCK_KEY);
        lock.addListener(listener);

        String expectedThreadName = Thread.currentThread().getName();
        causeLockRenewalFailure(lock);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).isEqualTo(expectedThreadName);
        assertThat(cause.get()).isNotNull();
    }

    @Test
    public void testLockRenewalFailureListenerReportsThreadIdWhenThreadNameIsUnknown() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        LockRenewalFailureListener listener = (name, error) -> {
            threadName.set(name);
            latch.countDown();
        };

        RLock lock = redisson.getLock(LOCK_KEY);
        lock.addListener(listener);

        // async API called on behalf of another thread, its name isn't available
        long ownerThreadId = Thread.currentThread().getId() + 1;
        lock.lockAsync(ownerThreadId).toCompletableFuture().join();
        redisson.getBucket(LOCK_KEY).set("not a lock");

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).isEqualTo(String.valueOf(ownerThreadId));
    }

    @Test
    public void testLockRenewalFailureListenerIsNotCalledOnSuccessfulRenewal() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        LockRenewalFailureListener listener = (name, error) -> calls.incrementAndGet();

        RLock lock = redisson.getLock(LOCK_KEY);
        lock.addListener(listener);
        lock.lock();
        try {
            Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 2);
            assertThat(calls).hasValue(0);
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testRemovedLockRenewalFailureListenerIsNotNotified() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        LockRenewalFailureListener listener = (name, error) -> calls.incrementAndGet();

        RLock lock = redisson.getLock(LOCK_KEY);
        int listenerId = lock.addListener(listener);
        lock.removeListener(listenerId);

        causeLockRenewalFailure(lock);
        Thread.sleep(LOCK_WATCHDOG_TIMEOUT * 3);

        assertThat(calls).hasValue(0);
    }

    @Test
    public void testLockRenewalFailureListenerIsRegisteredPerLockName() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        LockRenewalFailureListener listener = (name, error) -> latch.countDown();

        // listener added through one lock object, lock acquired through another
        redisson.getLock(LOCK_KEY).addListener(listener);
        causeLockRenewalFailure(redisson.getLock(LOCK_KEY));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testLockRenewalFailureListenerExceptionDoesNotStopScheduling() {
        AtomicInteger calls = new AtomicInteger();
        LockRenewalFailureListener listener = (name, error) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("listener failure");
        };

        RLock lock = redisson.getLock(LOCK_KEY);
        lock.addListener(listener);
        causeLockRenewalFailure(lock);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(calls.get()).isGreaterThanOrEqualTo(2));
    }

    @Test
    public void testLockRenewalFailureListenerIsNotifiedOnlyForItsOwnLock() throws InterruptedException {
        // one lock per renewal batch, so the healthy lock isn't renewed by the failing command
        recreateClient(1);

        AtomicInteger healthyLockCalls = new AtomicInteger();
        CountDownLatch failedLockNotification = new CountDownLatch(1);

        LockRenewalFailureListener healthyLockListener = (name, error) -> healthyLockCalls.incrementAndGet();
        LockRenewalFailureListener failedLockListener = (name, error) -> failedLockNotification.countDown();

        RLock healthyLock = redisson.getLock("healthy-lock");
        healthyLock.addListener(healthyLockListener);
        healthyLock.lock();

        RLock failedLock = redisson.getLock("failed-lock");
        failedLock.addListener(failedLockListener);
        causeLockRenewalFailure(failedLock);

        assertThat(failedLockNotification.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(LOCK_WATCHDOG_TIMEOUT / 2);

        assertThat(healthyLockCalls).hasValue(0);
    }

    @Test
    public void testSlowLockRenewalFailureListenerDoesNotDelayScheduling() throws InterruptedException {
        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch secondCallStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        LockRenewalFailureListener listener = (name, error) -> {
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
        };

        RLock lock = redisson.getLock(LOCK_KEY);
        lock.addListener(listener);
        causeLockRenewalFailure(lock);

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

    private void causeLockRenewalFailure(RLock lock) {
        lock.lock();
        redisson.getBucket(lock.getName()).set("not a lock");
    }

    private void recreateClient(int batchSize) {
        redisson.shutdown();
        redisson = createClient(batchSize);
    }

    private RedissonClient createClient(int batchSize) {
        Config c = createConfig(redis);
        c.setLockWatchdogTimeout(LOCK_WATCHDOG_TIMEOUT);
        c.setLockWatchdogBatchSize(batchSize);
        return Redisson.create(c);
    }

}
