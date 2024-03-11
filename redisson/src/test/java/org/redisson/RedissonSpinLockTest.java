package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class RedissonSpinLockTest extends BaseConcurrentTest {

    static class LockWithoutBoolean extends Thread {
        private CountDownLatch latch;
        private RedissonClient redisson;

        public LockWithoutBoolean(String name, CountDownLatch latch, RedissonClient redisson) {
            super(name);
            this.latch = latch;
            this.redisson = redisson;
        }

        public void run() {
            RLock lock = redisson.getSpinLock("lock");
            lock.lock(10, TimeUnit.MINUTES);
            System.out.println(Thread.currentThread().getName() + " gets lock. and interrupt: " + Thread.currentThread().isInterrupted());
            try {
                TimeUnit.MINUTES.sleep(1);
            } catch (InterruptedException e) {
                latch.countDown();
                Thread.currentThread().interrupt();
            } finally {
                try {
                    lock.unlock();
                } finally {
                    latch.countDown();
                }
            }
            System.out.println(Thread.currentThread().getName() + " ends.");
        }
    }

    @Test
    public void testRedisFailed() {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

        Assertions.assertThrows(RedisException.class, () -> {

            RLock lock = redisson.getSpinLock("myLock");
            // kill RedisServer while main thread is sleeping.
            redis.stop();
            Thread.sleep(3000);
            lock.tryLock(5, 10, TimeUnit.SECONDS);
        });

        redisson.shutdown();
    }

    @Test
    public void testTryLockWait() throws InterruptedException {
        testSingleInstanceConcurrency(1, r -> {
            RLock lock = r.getSpinLock("lock");
            lock.lock();
        });

        RLock lock = redisson.getSpinLock("lock");

        Awaitility.await().between(Duration.ofMillis(3000), Duration.ofMillis(3500)).untilAsserted(() -> {
            lock.tryLock(3, TimeUnit.SECONDS);
        });
    }

    @Test
    public void testLockUninterruptibly() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Thread thread_1 = new LockWithoutBoolean("thread-1", latch, redisson);
        Thread thread_2 = new LockWithoutBoolean("thread-2", latch, redisson);
        thread_1.start();

        TimeUnit.SECONDS.sleep(1); // let thread-1 get the lock
        thread_2.start();
        TimeUnit.SECONDS.sleep(1); // let thread_2 waiting for the lock
        thread_2.interrupt(); // interrupte the thread-2
        boolean res = latch.await(2, TimeUnit.SECONDS);
        assertThat(res).isFalse();
    }

    @Test
    public void testForceUnlock() {
        RLock lock = redisson.getSpinLock("lock");
        lock.lock();
        lock.forceUnlock();
        Assertions.assertFalse(lock.isLocked());

        lock = redisson.getSpinLock("lock");
        Assertions.assertFalse(lock.isLocked());
    }

    @Test
    public void testExpire() throws InterruptedException {
        RLock lock = redisson.getSpinLock("lock");
        lock.lock(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        Thread t = new Thread() {
            public void run() {
                RLock lock1 = redisson.getSpinLock("lock");
                lock1.lock();
                long spendTime = System.currentTimeMillis() - startTime;
                Assertions.assertTrue(spendTime < 2020);
                lock1.unlock();
            }

            ;
        };

        t.start();
        t.join();

        assertThatThrownBy(() -> {
            lock.unlock();
        }).isInstanceOf(IllegalMonitorStateException.class);
    }

    @Test
    public void testInCluster() throws Exception {
        testInCluster(client -> {
            Config config = client.getConfig();
            config.setSlavesSyncTimeout(3000);

            RedissonClient redisson = Redisson.create(config);
            RLock lock = redisson.getSpinLock("myLock");
            lock.lock();
            assertThat(lock.isLocked()).isTrue();
            lock.unlock();
            assertThat(lock.isLocked()).isFalse();
            redisson.shutdown();
        });
    }

    @Test
    public void testAutoExpire() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        RedissonClient r = createInstance();

        Thread t = new Thread() {
            @Override
            public void run() {
                RLock lock = r.getSpinLock("lock");
                lock.lock();
                latch.countDown();
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };

        t.start();

        Assertions.assertTrue(latch.await(1, TimeUnit.SECONDS));
        RLock lock = redisson.getSpinLock("lock");
        t.join();
        r.shutdown();

        await().atMost(redisson.getConfig().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS).until(() -> !lock.isLocked());
    }

    @Test
    public void testGetHoldCount() {
        RLock lock = redisson.getSpinLock("lock");
        Assertions.assertEquals(0, lock.getHoldCount());
        lock.lock();
        Assertions.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assertions.assertEquals(0, lock.getHoldCount());

        lock.lock();
        lock.lock();
        Assertions.assertEquals(2, lock.getHoldCount());
        lock.unlock();
        Assertions.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assertions.assertEquals(0, lock.getHoldCount());
    }

    @Test
    public void testIsHeldByCurrentThreadOtherThread() throws InterruptedException {
        RLock lock = redisson.getSpinLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getSpinLock("lock");
                Assertions.assertFalse(lock.isHeldByCurrentThread());
            }

            ;
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getSpinLock("lock");
                Assertions.assertFalse(lock.isHeldByCurrentThread());
            }

            ;
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsHeldByCurrentThread() {
        RLock lock = redisson.getSpinLock("lock");
        Assertions.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assertions.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assertions.assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    public void testIsLockedOtherThread() throws InterruptedException {
        RLock lock = redisson.getSpinLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getSpinLock("lock");
                Assertions.assertTrue(lock.isLocked());
            }

            ;
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getSpinLock("lock");
                Assertions.assertFalse(lock.isLocked());
            }

            ;
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsLocked() {
        RLock lock = redisson.getSpinLock("lock");
        Assertions.assertFalse(lock.isLocked());
        lock.lock();
        Assertions.assertTrue(lock.isLocked());
        lock.unlock();
        Assertions.assertFalse(lock.isLocked());
    }

    @Test
    public void testUnlockFail() {
        Assertions.assertThrows(IllegalMonitorStateException.class, () -> {
            RLock lock = redisson.getSpinLock("lock");
            Thread t = new Thread() {
                public void run() {
                    RLock lock = redisson.getSpinLock("lock");
                    lock.lock();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    lock.unlock();
                }

                ;
            };

            t.start();
            t.join(400);

            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                t.join();
                throw e;
            }
        });
    }


    @Test
    public void testLockUnlock() {
        Lock lock = redisson.getSpinLock("lock1");
        lock.lock();
        lock.unlock();

        lock.lock();
        lock.unlock();
    }

    @Test
    public void testReentrancy() throws InterruptedException {
        Lock lock = redisson.getSpinLock("lock1");
        Assertions.assertTrue(lock.tryLock());
        Assertions.assertTrue(lock.tryLock());
        lock.unlock();
        // next row  for test renew expiration tisk.
        //Thread.currentThread().sleep(TimeUnit.SECONDS.toMillis(RedissonLock.LOCK_EXPIRATION_INTERVAL_SECONDS*2));
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                RLock lock1 = redisson.getSpinLock("lock1");
                Assertions.assertFalse(lock1.tryLock());
            }
        };
        thread1.start();
        thread1.join();
        lock.unlock();
    }


    @Test
    public void testConcurrency_SingleInstance() throws InterruptedException {
        final AtomicInteger lockedCounter = new AtomicInteger();

        int iterations = 15;
        testSingleInstanceConcurrency(iterations, r -> {
            Lock lock = r.getSpinLock("testConcurrency_SingleInstance");
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assertions.assertEquals(iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrencyLoop_MultiInstance() throws InterruptedException {
        final int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(16, r -> {
            for (int i = 0; i < iterations; i++) {
                r.getSpinLock("testConcurrency_MultiInstance1").lock();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                r.getSpinLock("testConcurrency_MultiInstance1").unlock();
            }
        });

        Assertions.assertEquals(16 * iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrency_MultiInstance() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(iterations, r -> {
            Lock lock = r.getSpinLock("testConcurrency_MultiInstance2");
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assertions.assertEquals(iterations, lockedCounter.get());
    }

    @Test
    public void testTryLockAsync() throws InterruptedException {
        RLock lock = redisson.getSpinLock("lock");
        lock.lock();

        AtomicBoolean lockAsyncSucceed = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            RFuture<Void> booleanRFuture = lock.lockAsync();
            booleanRFuture.whenComplete((res, e) -> {
                if (e != null) {
                    Assertions.fail("Lock aquire failed for some reason");
                }
                lockAsyncSucceed.set(true);
            });
        });
        thread.start();
        Thread.sleep(1_000);
        assertThat(lockAsyncSucceed.get()).isFalse();
        lock.unlock();
        Thread.sleep(200);
        assertThat(lockAsyncSucceed.get()).isTrue();
        lock.forceUnlock();
    }

    @Test
    public void testTimedTryLockAsync() throws InterruptedException {
        RLock lock = redisson.getSpinLock("lock");
        lock.lock();

        AtomicBoolean lockAsyncSucceed = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            RFuture<Boolean> booleanRFuture = lock.tryLockAsync(1, 30, TimeUnit.SECONDS);
            booleanRFuture.whenComplete((res, e) -> {
                if (e != null) {
                    Assertions.fail("Lock aquire failed for some reason");
                }
                lockAsyncSucceed.set(res);
            });
        });
        thread.start();
        Thread.sleep(500);
        assertThat(lockAsyncSucceed.get()).isFalse();
        lock.unlock();
        Thread.sleep(200);
        assertThat(lockAsyncSucceed.get()).isTrue();
        lock.forceUnlock();
    }


    @Test
    public void testTryLockAsyncWaitTime() throws InterruptedException {
        RLock lock = redisson.getSpinLock("lock");
        lock.lock();

        AtomicBoolean lockAsyncSucceed = new AtomicBoolean(true);
        Thread thread = new Thread(() -> {
            RFuture<Boolean> booleanRFuture = lock.tryLockAsync(1, 30, TimeUnit.SECONDS);
            booleanRFuture.whenComplete((res, e) -> {
                if (e != null) {
                    Assertions.fail("Lock aquire failed for some reason");
                }
                lockAsyncSucceed.set(res);
            });
        });
        thread.start();
        Thread.sleep(1500);
        assertThat(lockAsyncSucceed.get()).isFalse();
        lock.forceUnlock();
    }

    @Test
    public void testTryLockAsyncFailed() throws InterruptedException {
        RLock lock = redisson.getSpinLock("lock");
        lock.lock();

        AtomicBoolean lockAsyncSucceed = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            RFuture<Boolean> booleanRFuture = lock.tryLockAsync();
            booleanRFuture.whenComplete((res, e) -> {
                if (e != null) {
                    Assertions.fail("Lock aquire failed for some reason");
                }
                lockAsyncSucceed.set(res);
            });
        });
        thread.start();
        Thread.sleep(1_000);
        assertThat(lockAsyncSucceed.get()).isFalse();
        lock.unlock();
    }

    @Test
    public void testTryLockAsyncSucceed() throws InterruptedException, ExecutionException {
        RLock lock = redisson.getSpinLock("lock");

        Boolean result = lock.tryLockAsync().get();
        assertThat(result).isTrue();
        lock.unlock();
    }
}
