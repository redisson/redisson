package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.*;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class RedissonLockTest extends BaseConcurrentTest {

    static class LockWithoutBoolean extends Thread {
        private CountDownLatch latch;
        private RedissonClient redisson;

        public LockWithoutBoolean(String name, CountDownLatch latch, RedissonClient redisson) {
            super(name);
            this.latch = latch;
            this.redisson = redisson;
        }

        public void run() {
            RLock lock = redisson.getLock("lock");
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

    public static class LockThread implements Runnable {

        AtomicBoolean hasFails;
        RedissonClient redissonClient;
        String lockName;

        public LockThread(AtomicBoolean hasFails, RedissonClient redissonClient, String lockName) {
            this.hasFails = hasFails;
            this.redissonClient = redissonClient;
            this.lockName = lockName;
        }

        @Override
        public void run() {
            RLock lock = redissonClient.getLock(lockName);
            try {
                boolean bLocked = lock.tryLock(100, -1, TimeUnit.MILLISECONDS);
                if (bLocked) {
                    lock.unlock();
                } else {
                    hasFails.set(true);
                }
            } catch (Exception ex) {
                hasFails.set(true);
            }
        }
    }

    @Test
    public void testSubscriptionsPerConnection() throws InterruptedException {
        Config config = createConfig();
        config.useSingleServer()
                .setSubscriptionConnectionPoolSize(1)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionsPerConnection(1)
                .setAddress(redisson.getConfig().useSingleServer().getAddress());

        RedissonClient redisson  = Redisson.create(config);
        ExecutorService e = Executors.newFixedThreadPool(32);
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger ops = new AtomicInteger();
        for (int i = 0; i < 5000; i++) {
            int j = i;
            e.submit(() -> {
                try {
                    String lockKey = "lock-" + ThreadLocalRandom.current().nextInt(5);
                    RLock lock = redisson.getLock(lockKey);
                    lock.lock();
                    Thread.sleep(ThreadLocalRandom.current().nextInt(20));
                    lock.unlock();
                    ops.incrementAndGet();
                } catch (Exception exception) {
                    exception.printStackTrace();
                    if(exception instanceof RedisTimeoutException){
                        return;
                    }
                    errors.incrementAndGet();
                }
            });
        }

        e.shutdown();
        assertThat(e.awaitTermination(150, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get()).isZero();

        RedisClientConfig cc = new RedisClientConfig();
        cc.setAddress(redisson.getConfig().useSingleServer().getAddress());
        RedisClient c = RedisClient.create(cc);
        RedisConnection ccc = c.connect();
        List<String> channels = ccc.sync(RedisCommands.PUBSUB_CHANNELS);
        assertThat(channels).isEmpty();
        c.shutdown();

        redisson.shutdown();
    }

    @Test
    public void testSinglePubSub() throws InterruptedException, ExecutionException {
        Config config = createConfig();
        config.useSingleServer()
            .setSubscriptionConnectionPoolSize(1)
            .setSubscriptionsPerConnection(1);
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        RedissonClient redissonClient = Redisson.create(config);
        AtomicBoolean hasFails = new AtomicBoolean();

        for (int i = 0; i < 2; i++) {
            Future<?> f1 = executorService.submit(new LockThread(hasFails, redissonClient, "Lock1_" + i));
            Future<?> f2 = executorService.submit(new LockThread(hasFails, redissonClient, "Lock1_" + i));
            Future<?> f3 = executorService.submit(new LockThread(hasFails, redissonClient, "Lock2_" + i));
            Future<?> f4 = executorService.submit(new LockThread(hasFails, redissonClient, "Lock2_" + i));
            f1.get();
            f2.get();
            f3.get();
            f4.get();
        }

        assertThat(hasFails).isFalse();
        redissonClient.shutdown();
    }

    @Test
    public void testRedisFailed() {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);

        Assertions.assertThrows(WriteRedisConnectionException.class, () -> {

            RLock lock = redisson.getLock("myLock");
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
            RLock lock = r.getLock("lock");
            lock.lock();
        });

        RLock lock = redisson.getLock("lock");
        
        long startTime = System.currentTimeMillis();
        lock.tryLock(3, TimeUnit.SECONDS);
        assertThat(System.currentTimeMillis() - startTime).isBetween(2990L, 3350L);
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
        RLock lock = redisson.getLock("lock");
        lock.lock();
        lock.forceUnlock();
        Assertions.assertFalse(lock.isLocked());

        lock = redisson.getLock("lock");
        Assertions.assertFalse(lock.isLocked());
    }

    @Test
    public void testExpire() throws InterruptedException {
        RLock lock = redisson.getLock("lock");
        lock.lock(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        Thread t = new Thread() {
            public void run() {
                RLock lock1 = redisson.getLock("lock");
                lock1.lock();
                long spendTime = System.currentTimeMillis() - startTime;
                Assertions.assertTrue(spendTime < 2020);
                lock1.unlock();
            };
        };

        t.start();
        t.join();

        assertThatThrownBy(() -> {
            lock.unlock();
        }).isInstanceOf(IllegalMonitorStateException.class);
    }

    @Test
    public void testRemainTimeToLive() {
        RLock lock = redisson.getLock("test-lock:1");
        lock.lock(1, TimeUnit.HOURS);
        assertThat(lock.remainTimeToLive()).isBetween(TimeUnit.HOURS.toMillis(1) - 10, TimeUnit.HOURS.toMillis(1));
        lock.unlock();
        assertThat(lock.remainTimeToLive()).isEqualTo(-2);
        lock.lock();
        assertThat(lock.remainTimeToLive()).isBetween(redisson.getConfig().getLockWatchdogTimeout() - 10, redisson.getConfig().getLockWatchdogTimeout());
    }

    @Test
    public void testInCluster() throws Exception {
        testInCluster(redisson -> {
            RLock lock = redisson.getLock("myLock");
            lock.lock();
            assertThat(lock.isLocked()).isTrue();
            lock.unlock();
            assertThat(lock.isLocked()).isFalse();
        });
    }


    @Test
    public void testAutoExpire() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        
        RedissonClient r = createInstance();
        
        Thread t = new Thread() {
            @Override
            public void run() {
                RLock lock = r.getLock("lock");
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
        RLock lock = redisson.getLock("lock");
        t.join();
        r.shutdown();
        
        await().atMost(redisson.getConfig().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS).until(() -> !lock.isLocked());
    }

    @Test
    public void testGetHoldCount() {
        RLock lock = redisson.getLock("lock");
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
        RLock lock = redisson.getLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assertions.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assertions.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsHeldByCurrentThread() {
        RLock lock = redisson.getLock("lock");
        Assertions.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assertions.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assertions.assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    public void testIsLockedOtherThread() throws InterruptedException {
        RLock lock = redisson.getLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assertions.assertTrue(lock.isLocked());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assertions.assertFalse(lock.isLocked());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsLocked() {
        RLock lock = redisson.getLock("lock");
        Assertions.assertFalse(lock.isLocked());
        lock.lock();
        Assertions.assertTrue(lock.isLocked());
        lock.unlock();
        Assertions.assertFalse(lock.isLocked());
    }

    @Test
    public void testUnlockFail() {
        Assertions.assertThrows(IllegalMonitorStateException.class, () -> {
            RLock lock = redisson.getLock("lock");
            Thread t = new Thread() {
                public void run() {
                    RLock lock = redisson.getLock("lock");
                    lock.lock();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    lock.unlock();
                };
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
        Lock lock = redisson.getLock("lock1");
        lock.lock();
        lock.unlock();

        lock.lock();
        lock.unlock();
    }

    @Test
    public void testReentrancy() throws InterruptedException {
        Lock lock = redisson.getLock("lock1");
        Assertions.assertTrue(lock.tryLock());
        Assertions.assertTrue(lock.tryLock());
        lock.unlock();
        // next row  for test renew expiration tisk.
        //Thread.currentThread().sleep(TimeUnit.SECONDS.toMillis(RedissonLock.LOCK_EXPIRATION_INTERVAL_SECONDS*2));
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                RLock lock1 = redisson.getLock("lock1");
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
            Lock lock = r.getLock("testConcurrency_SingleInstance");
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
                r.getLock("testConcurrency_MultiInstance1").lock();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                r.getLock("testConcurrency_MultiInstance1").unlock();
            }
        });

        Assertions.assertEquals(16 * iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrency_MultiInstance() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(iterations, r -> {
            Lock lock = r.getLock("testConcurrency_MultiInstance2");
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assertions.assertEquals(iterations, lockedCounter.get());
    }

}
