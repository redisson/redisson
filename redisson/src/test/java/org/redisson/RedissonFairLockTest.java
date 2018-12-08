package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.client.codec.LongCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedissonFairLockTest extends BaseConcurrentTest {

    private final Logger log = LoggerFactory.getLogger(RedissonFairLockTest.class);
    
    @Test
    public void testTimeoutDrift() throws Exception {
        int leaseTimeSeconds = 30;
        RLock lock = redisson.getFairLock("test-fair-lock");
        AtomicBoolean lastThreadTryingToLock = new AtomicBoolean(false);


        //create a scenario where the same 3 threads keep on trying to get a lock
        //to exacerbate the problem, use a very short wait time and a long lease time
        //this will end up setting the queue timeout score to a value far away in the future
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    if (lock.tryLock(500, leaseTimeSeconds * 1000, TimeUnit.MILLISECONDS)) {
                        log.info("Lock taken by thread " + Thread.currentThread().getId());
                        Thread.sleep(30000);
                        try {
                            //this could fail before use sleep for the same value as the lock expiry, that's fine
                            //for the purpose of this test
                            lock.unlock();
                            log.info("Lock released by thread " + Thread.currentThread().getId());
                        } catch (Exception ignored) {
                        }
                    }
                } catch (InterruptedException ex) {
                    log.warn("Interrupted");
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }
            });
            //for the first 3 threads, add a 50ms delay. This is to recreate the worst case scenario, where all threads
            //attempting to lock do so in a staggered pattern. This delay will be carried over by the thread pool.
            if (i < 3) {
                Thread.sleep(50);
            }
        }
        //we now launch one more thread and kill it before it manages to fail and clean up
        //that thread will end up with a timeout that will prevent any others from taking the lock for a long time
        executor.submit(() -> {
            log.info("Final thread trying to take the lock with thread id: " + Thread.currentThread().getId());
            try {
                lastThreadTryingToLock.set(true);
                if (lock.tryLock(30000, 30000, TimeUnit.MILLISECONDS)) {
                    log.info("Lock taken by final thread " + Thread.currentThread().getId());
                    Thread.sleep(1000);
                    lock.unlock();
                    log.info("Lock released by final thread " + Thread.currentThread().getId());
                }
            } catch (InterruptedException ex) {
                log.warn("Interrupted");
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        });
        //now we wait for all others threads to stop trying, and only the last thread is running
        while (!lastThreadTryingToLock.get()) {
            Thread.sleep(100);
        }
        //try to kill that last thread, and don't let it clean up after itself
        executor.shutdownNow();
        //force the lock to unlock just in case
        try {
            lock.forceUnlock();
        } catch (Exception ignored) {
        }
        if (lock.isLocked()) {
            Assert.fail("Lock should have been unlocked by now");
        }
        //check the timeout scores - they should all be within a reasonable amount of time from now
        List<Long> queue = redisson.getScript(LongCodec.INSTANCE).eval(RScript.Mode.READ_ONLY,
                "local result = {}; " +
                        "local timeouts = redis.call('zrange', KEYS[1], 0, 99, 'WITHSCORES'); " +
                        "for i=1,#timeouts,2 do " +
                        "table.insert(result, timeouts[i+1]); " +
                        "end; " +
                        "return result; ",
                RScript.ReturnType.MULTI,
                Collections.singletonList("redisson_lock_timeout:{test-fair-lock}"));

        int i = 0;
        for (Long timeout : queue) {
            long epiry = ((timeout - new Date().getTime()) / 1000);
            log.info("Item " + (i++) + " expires in " + epiry + " seconds");
            //the Redisson library uses this 5000ms delay in the code
            if (epiry > leaseTimeSeconds + 5) {
                Assert.fail("It would take more than " + leaseTimeSeconds + "s to get the lock!");
            }
        }
    }
    
    @Test
    public void testTryLockNonDelayed() throws InterruptedException {
        String LOCK_NAME = "SOME_LOCK";
        
        Thread t1 = new Thread(() -> {
            RLock fairLock = redisson.getFairLock(LOCK_NAME);
            try {
                if (fairLock.tryLock(0, TimeUnit.SECONDS)) {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    Assert.fail("Unable to acquire lock for some reason");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                fairLock.unlock();
            }
        });
        
        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            RLock fairLock = redisson.getFairLock(LOCK_NAME);
            try {
                if (fairLock.tryLock(200, TimeUnit.MILLISECONDS)) {
                    Assert.fail("Should not be inside second block");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                fairLock.unlock();
            }
        });
        
        t1.start();
        t2.start();
        
        t1.join();
        t2.join();
        
        RLock fairLock = redisson.getFairLock(LOCK_NAME);
        try {
            if (!fairLock.tryLock(0, TimeUnit.SECONDS)) {
                Assert.fail("Could not get unlocked lock " + LOCK_NAME);
            }
        } finally {
            fairLock.unlock();
        }
    }
    
    @Test
    public void testTryLockWait() throws InterruptedException {
        testSingleInstanceConcurrency(1, r -> {
            RLock lock = r.getFairLock("lock");
            lock.lock();
        });

        RLock lock = redisson.getFairLock("lock");
        
        long startTime = System.currentTimeMillis();
        lock.tryLock(3, TimeUnit.SECONDS);
        assertThat(System.currentTimeMillis() - startTime).isBetween(2990L, 3100L);
    }
    
    @Test
    public void testDelete() {
        RLock lock = redisson.getFairLock("lock");
        Assert.assertFalse(lock.delete());

        lock.lock();
        Assert.assertTrue(lock.delete());
    }

    @Test
    public void testForceUnlock() {
        RLock lock = redisson.getFairLock("lock");
        lock.lock();
        lock.forceUnlock();
        Assert.assertFalse(lock.isLocked());

        lock = redisson.getFairLock("lock");
        Assert.assertFalse(lock.isLocked());
    }

    @Test
    public void testExpire() throws InterruptedException {
        RLock lock = redisson.getFairLock("lock");
        lock.lock(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        Thread t = new Thread() {
            public void run() {
                RLock lock1 = redisson.getFairLock("lock");
                lock1.lock();

                long spendTime = System.currentTimeMillis() - startTime;
                System.out.println(spendTime);
                Assert.assertTrue(spendTime < 2020);
                lock1.unlock();
            };
        };

        t.start();
        t.join();

        lock.unlock();
    }

    @Test
    public void testAutoExpire() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        testSingleInstanceConcurrency(1, r -> {
            RLock lock = r.getFairLock("lock");
            lock.lock();
            latch.countDown();
        });

        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
        RLock lock = redisson.getFairLock("lock");
        
        await().atMost(redisson.getConfig().getLockWatchdogTimeout() + 1000, TimeUnit.MILLISECONDS).until(() -> !lock.isLocked());
    }

    @Test
    public void testGetHoldCount() {
        RLock lock = redisson.getFairLock("lock");
        Assert.assertEquals(0, lock.getHoldCount());
        lock.lock();
        Assert.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(0, lock.getHoldCount());

        lock.lock();
        lock.lock();
        Assert.assertEquals(2, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(0, lock.getHoldCount());
    }

    @Test
    public void testIsHeldByCurrentThreadOtherThread() throws InterruptedException {
        RLock lock = redisson.getFairLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
                Assert.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
                Assert.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsHeldByCurrentThread() {
        RLock lock = redisson.getFairLock("lock");
        Assert.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assert.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assert.assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    public void testIsLockedOtherThread() throws InterruptedException {
        RLock lock = redisson.getFairLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
                Assert.assertTrue(lock.isLocked());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
                Assert.assertFalse(lock.isLocked());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsLocked() {
        RLock lock = redisson.getFairLock("lock");
        Assert.assertFalse(lock.isLocked());
        lock.lock();
        Assert.assertTrue(lock.isLocked());
        lock.unlock();
        Assert.assertFalse(lock.isLocked());
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockFail() throws InterruptedException {
        RLock lock = redisson.getFairLock("lock");
        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getFairLock("lock");
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
    }


    @Test
    public void testLockUnlock() {
        Lock lock = redisson.getFairLock("lock1");
        lock.lock();
        lock.unlock();

        lock.lock();
        lock.unlock();
    }

    @Test
    public void testReentrancy() throws InterruptedException {
        Lock lock = redisson.getFairLock("lock1");
        Assert.assertTrue(lock.tryLock());
        Assert.assertTrue(lock.tryLock());
        lock.unlock();
        // next row  for test renew expiration tisk.
        //Thread.currentThread().sleep(TimeUnit.SECONDS.toMillis(RedissonLock.LOCK_EXPIRATION_INTERVAL_SECONDS*2));
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                RLock lock1 = redisson.getFairLock("lock1");
                Assert.assertFalse(lock1.tryLock());
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
            Lock lock = r.getFairLock("testConcurrency_SingleInstance");
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assert.assertEquals(iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrencyLoop_MultiInstance() throws InterruptedException {
        final int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(16, r -> {
            for (int i = 0; i < iterations; i++) {
                r.getFairLock("testConcurrency_MultiInstance1").lock();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                r.getFairLock("testConcurrency_MultiInstance1").unlock();
            }
        });

        Assert.assertEquals(16 * iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrency_MultiInstance() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(iterations, r -> {
            Lock lock = r.getFairLock("testConcurrency_MultiInstance2");
            lock.lock();
            lockedCounter.incrementAndGet();
            lock.unlock();
        });

        Assert.assertEquals(iterations, lockedCounter.get());
    }
    
    @Test
    public void testConcurrency_MultiInstance_Ordering() throws InterruptedException {
        final ConcurrentLinkedQueue<Thread> queue = new ConcurrentLinkedQueue<>();
        final AtomicInteger lockedCounter = new AtomicInteger();

        int totalThreads = Runtime.getRuntime().availableProcessors()*2;
        for (int i = 0; i < totalThreads; i++) {
            Thread t1 = new Thread(() -> {
                Lock lock = redisson.getFairLock("testConcurrency_MultiInstance2");
                queue.add(Thread.currentThread());
                lock.lock();
                Thread t = queue.poll();
                assertThat(t).isEqualTo(Thread.currentThread());
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                lockedCounter.incrementAndGet();
                lock.unlock();
            });
            Thread.sleep(10);
            t1.start();
        }
        
        await().atMost(30, TimeUnit.SECONDS).until(() -> lockedCounter.get() == totalThreads);
    }


}
