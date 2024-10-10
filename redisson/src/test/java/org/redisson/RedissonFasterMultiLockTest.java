package org.redisson;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.misc.Hash;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;


public class RedissonFasterMultiLockTest extends BaseConcurrentTest {

    @Test
    public void testSimpleLock() {
        String group = "class:1000";
        String field1 = "student:001";
        String field2 = "student:002";
        long threadId = Thread.currentThread().getId();
        RMap<String, String> mapCache = redisson.getMap(group, StringCodec.INSTANCE);
        mapCache.delete();
        RLock lock = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        lock.lock(30, TimeUnit.SECONDS);
        RedissonObject redissonObject = (RedissonObject) (lock);
        assertThat(mapCache.get(hashValue(redissonObject, field1))).isNotBlank();
        assertThat("1".equals(mapCache.get(hashValue(redissonObject, field1) + ":" + ((RedissonBaseLock) (lock)).getLockName(threadId) + ":lock_count"))).isTrue();
        lock.unlock();
        assertThat(mapCache.isExists()).isFalse();

    }
    @Test
    public void testReentrantLock(){
        String group = "class:1000";
        String field1 = "student:001";
        String field2 = "student:002";
        long threadId = Thread.currentThread().getId();
        RMap<String, String> mapCache = redisson.getMap(group, StringCodec.INSTANCE);
        mapCache.delete();

        RLock lock1 = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        RLock lock2 = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        lock1.lock(30, TimeUnit.SECONDS);
        lock2.lock(30, TimeUnit.SECONDS);
        RedissonObject redissonObject1 = (RedissonObject) (lock1);
        assertThat(mapCache.get(hashValue(redissonObject1, field1))).isNotBlank();
        assertThat("2".equals(mapCache.get(hashValue(redissonObject1, field1) + ":" + ((RedissonBaseLock) (lock1)).getLockName(threadId) + ":lock_count"))).isTrue();
        assertThat(mapCache.get(hashValue(redissonObject1, field2))).isNotBlank();
        assertThat("2".equals(mapCache.get(hashValue(redissonObject1, field2) + ":" + ((RedissonBaseLock) (lock1)).getLockName(threadId) + ":lock_count"))).isTrue();
        lock1.unlock();
        assertThat(mapCache.get(hashValue(redissonObject1, field1))).isNotBlank();
        assertThat("1".equals(mapCache.get(hashValue(redissonObject1, field1) + ":" + ((RedissonBaseLock) (lock1)).getLockName(threadId) + ":lock_count"))).isTrue();
        assertThat(mapCache.get(hashValue(redissonObject1, field2))).isNotBlank();
        assertThat("1".equals(mapCache.get(hashValue(redissonObject1, field2) + ":" + ((RedissonBaseLock) (lock1)).getLockName(threadId) + ":lock_count"))).isTrue();
        lock1.unlock();
        assertThat(mapCache.isExists()).isFalse();
    }

    private String hashValue(RedissonObject redissonObject,Object key) {
        ByteBuf objectState = redissonObject.encode(key);
        try {
            return Hash.hash128toBase64(objectState);
        } finally {
            objectState.release();
        }
    }
    @Test
    public void testForceUnLock(){
        String group = "class:1000";
        String field1 = "student:001";
        String field2 = "student:002";
        RMap<String, String> mapCache = redisson.getMap(group, StringCodec.INSTANCE);
        mapCache.delete();
        RLock lock = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        lock.lock(30, TimeUnit.SECONDS);
        lock.forceUnlock();
        assertThat(mapCache.isExists()).isFalse();
    }
    @Test
    public void testRenewExpiration() throws InterruptedException {
        String group = "class:1000";
        String field1 = "student:001";
        String field2 = "student:002";
        RMap<String, String> mapCache = redisson.getMap(group, StringCodec.INSTANCE);
        mapCache.delete();

        RLock lock = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        String lockName = ((RedissonFasterMultiLock) lock).getLockName(Thread.currentThread().getId());
        lock.lock();
        RedissonObject redissonObject = (RedissonObject) (lock);
        long fieldExpireTime1 = Long.valueOf(mapCache.get(hashValue(redissonObject, field1) + ":" + lockName + ":expire_time"));
        long fieldExpireTime2 = Long.valueOf(mapCache.get(hashValue(redissonObject, field2) + ":" + lockName + ":expire_time"));
        Thread.sleep(Duration.ofSeconds(17));
        long fieldExpireTime11 = Long.valueOf(mapCache.get(hashValue(redissonObject, field1) + ":" + lockName + ":expire_time"));
        long fieldExpireTime22 = Long.valueOf(mapCache.get(hashValue(redissonObject, field2) + ":" + lockName + ":expire_time"));

        assertThat(fieldExpireTime11 > fieldExpireTime1).isTrue();
        assertThat(fieldExpireTime22 > fieldExpireTime2).isTrue();

        lock.unlock();
    }

    @Test
    public void testTryLock() throws ExecutionException, InterruptedException {
        String group = "class:1000";
        String field1 = "student:001";
        String field2 = "student:002";
        RMap<String, String> mapCache = redisson.getMap(group, StringCodec.INSTANCE);
        mapCache.delete();
        RLock lock = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        boolean result = lock.tryLock();
        assertThat(result).isTrue();
        result  = lock.tryLockAsync(999).get();
        assertThat(result).isFalse();
        lock.unlock();
    }


    @Test
    public void testTryLockWait() throws InterruptedException, ExecutionException {
        String group = "class:1000";
        String field1 = "student:001";
        String field2 = "student:002";
        RMap<String, String> mapCache = redisson.getMap(group, StringCodec.INSTANCE);
        mapCache.delete();
        RLock lock1 = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        lock1.lock(2, TimeUnit.SECONDS);
        long startTime = System.currentTimeMillis();
        RLock lock2 = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        boolean result = lock2.tryLockAsync(3, 5, TimeUnit.SECONDS, 999).get();
        long endTime = System.currentTimeMillis();
        assertThat(result).isTrue();
        assertThat((endTime - startTime) > 2 * 1000).isTrue();
        lock2.unlockAsync(999).get();
    }

    @Test
    public void testIsHeldAndIsLockedByThread(){
        String group = "class:1000";
        String field1 = "student:001";
        String field2 = "student:002";
        RMap<String, String> mapCache = redisson.getMap(group, StringCodec.INSTANCE);
        mapCache.delete();
        RLock lock = redisson.getMultiLock(group, Arrays.asList(field1, field2));
        lock.lock();
        assertThat(lock.isHeldByCurrentThread()).isTrue();
        assertThat(lock.isLocked()).isTrue();
        lock.unlock();
        assertThat(lock.isHeldByCurrentThread()).isFalse();
        assertThat(lock.isLocked()).isFalse();
    }

    static class LockWithoutBoolean extends Thread {
        private CountDownLatch latch;
        private RedissonClient redisson;

        public LockWithoutBoolean(String name, CountDownLatch latch, RedissonClient redisson) {
            super(name);
            this.latch = latch;
            this.redisson = redisson;
        }

        public void run() {
            RLock lock = redisson.getMultiLock("batchLock:thread", Arrays.asList("test1", "test2"));
            lock.lock(3, TimeUnit.SECONDS);
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
            RLock lock = redissonClient.getMultiLock(lockName,Arrays.asList("test1","test2"));
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
            e.submit(() -> {
                try {
                    String lockKey = "lock-" + ThreadLocalRandom.current().nextInt(5);
                    RLock lock = redisson.getMultiLock(lockKey, Arrays.asList("test1","test2"));
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
    public void testLockIsNotRenewedAfterInterruptedTryLock() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        RLock lock = redisson.getMultiLock("batchLock:" + UUID.randomUUID(), Arrays.asList("test1", "test2"));
        assertThat(lock.isLocked()).isFalse();

        Thread thread = new Thread(() -> {
            countDownLatch.countDown();
            if (!lock.tryLock()) {
                return;
            }
            lock.unlock();
        });
        thread.start();
        countDownLatch.await();
        // let the tcp request be sent out
        TimeUnit.MILLISECONDS.sleep(5);
        thread.interrupt();
        TimeUnit.SECONDS.sleep(45);

        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    public void testRedisFailed() {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

        Assertions.assertThrows(RedisException.class, () -> {

            RLock lock = redisson.getMultiLock("batchLock:" + UUID.randomUUID(), Arrays.asList("test1", "test2"));
            // kill RedisServer while main thread is sleeping.
            redis.stop();
            Thread.sleep(3000);
            lock.tryLock(5, 10, TimeUnit.SECONDS);
        });

        redisson.shutdown();
    }


    @Test
    public void testLockUnInterruptibly() throws InterruptedException {
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
        TimeUnit.SECONDS.sleep(1);// let thread_2 get lock and release channel

    }

    @Test
    public void testExpire() throws InterruptedException {
        RLock lock = redisson.getMultiLock("testExpire",Arrays.asList("test1","test2"));
        lock.lock(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        Thread t = new Thread() {
            public void run() {
                RLock lock1 = redisson.getMultiLock("testExpire",Arrays.asList("test1","test2"));
                lock1.lock();
                long spendTime = System.currentTimeMillis() - startTime;
                Assertions.assertTrue(spendTime < 2020);
                lock1.unlock();
            };
        };

        t.start();
        t.join();
        org.assertj.core.api.Assertions.setMaxStackTraceElementsDisplayed(50);
        assertThatThrownBy(() -> {
            lock.unlock();
        }).isInstanceOf(IllegalMonitorStateException.class);
    }

    @Test
    public void testInCluster() throws Exception {
        testInCluster(redisson -> {
            for (int i = 0; i < 3; i++) {
                RLock lock = redisson.getMultiLock("testInCluster",Arrays.asList("test1","test2"));
                lock.lock();
                assertThat(lock.isLocked()).isTrue();
                lock.unlock();
                assertThat(lock.isLocked()).isFalse();
            }
        });
    }


    @Test
    public void testAutoExpire() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        RedissonClient r = createInstance();

        Thread t = new Thread() {
            @Override
            public void run() {
                RLock lock = r.getMultiLock("testAutoExpire",Arrays.asList("test1","test2"));
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
        RLock lock = redisson.getMultiLock("testAutoExpire",Arrays.asList("test1","test2"));
        t.join();
        r.shutdown();

        await().atMost(redisson.getConfig().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS).until(() -> !lock.isLocked());
    }


    @Test
    public void testIsHeldByCurrentThreadOtherThread() throws InterruptedException {
        RLock lock = redisson.getMultiLock("testIsHeldByCurrentThreadOtherThread",Arrays.asList("test1","test2"));
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getMultiLock("testIsHeldByCurrentThreadOtherThread",Arrays.asList("test1","test2"));
                Assertions.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getMultiLock("testIsHeldByCurrentThreadOtherThread",Arrays.asList("test1","test2"));
                Assertions.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsHeldByCurrentThread() {
        RLock lock = redisson.getMultiLock("testIsHeldByCurrentThread",Arrays.asList("test1","test2"));
        Assertions.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assertions.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assertions.assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    public void testIsLockedOtherThread() throws InterruptedException {
        RLock lock = redisson.getMultiLock("testIsLockedOtherThread",Arrays.asList("test1","test2"));
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getMultiLock("testIsLockedOtherThread",Arrays.asList("test1","test2"));
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
        RLock lock = redisson.getMultiLock("testIsLocked",Arrays.asList("test1","test2"));
        Assertions.assertFalse(lock.isLocked());
        lock.lock();
        Assertions.assertTrue(lock.isLocked());
        lock.unlock();
        Assertions.assertFalse(lock.isLocked());
    }

    @Test
    public void testUnlockFail() {
        Assertions.assertThrows(IllegalMonitorStateException.class, () -> {
            RLock lock = redisson.getMultiLock("testUnlockFail",Arrays.asList("test1","test2"));
            Thread t = new Thread() {
                public void run() {
                    RLock lock = redisson.getMultiLock("testUnlockFail",Arrays.asList("test1","test2"));
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
    public void testReentrancy() throws InterruptedException {
        Lock lock = redisson.getMultiLock("testUnlockFail",Arrays.asList("test1","test2"));
        Assertions.assertTrue(lock.tryLock());
        Assertions.assertTrue(lock.tryLock());
        lock.unlock();
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                RLock lock1 = redisson.getMultiLock("testUnlockFail",Arrays.asList("test1","test2"));
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
            Lock lock = r.getMultiLock("testConcurrency_SingleInstance",Arrays.asList("test1","test2"));
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
                r.getMultiLock("testConcurrencyLoop_MultiInstance",Arrays.asList("test1","test2")).lock();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                r.getMultiLock("testConcurrencyLoop_MultiInstance",Arrays.asList("test1","test2")).unlock();
            }
        });

        Assertions.assertEquals(16 * iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrency_MultiInstance() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(iterations, r -> {
            Lock lock = r.getMultiLock("testConcurrency_MultiInstance2",Arrays.asList("test1","test2"));
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assertions.assertEquals(iterations, lockedCounter.get());
    }





}
