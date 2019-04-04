package org.redisson;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import static org.awaitility.Awaitility.*;

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
    
    @Test
    public void testTryLockWait() throws InterruptedException {
        testSingleInstanceConcurrency(1, r -> {
            RLock lock = r.getLock("lock");
            lock.lock();
        });

        RLock lock = redisson.getLock("lock");
        
        long startTime = System.currentTimeMillis();
        lock.tryLock(3, TimeUnit.SECONDS);
        assertThat(System.currentTimeMillis() - startTime).isBetween(2990L, 3100L);
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
        Assert.assertFalse(lock.isLocked());

        lock = redisson.getLock("lock");
        Assert.assertFalse(lock.isLocked());
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
                Assert.assertTrue(spendTime < 2020);
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

        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
        RLock lock = redisson.getLock("lock");
        t.join();
        r.shutdown();
        
        await().atMost(redisson.getConfig().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS).until(() -> !lock.isLocked());
    }

    @Test
    public void testGetHoldCount() {
        RLock lock = redisson.getLock("lock");
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
        RLock lock = redisson.getLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assert.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assert.assertFalse(lock.isHeldByCurrentThread());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsHeldByCurrentThread() {
        RLock lock = redisson.getLock("lock");
        Assert.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assert.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assert.assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    public void testIsLockedOtherThread() throws InterruptedException {
        RLock lock = redisson.getLock("lock");
        lock.lock();

        Thread t = new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assert.assertTrue(lock.isLocked());
            };
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assert.assertFalse(lock.isLocked());
            };
        };

        t2.start();
        t2.join();
    }

    @Test
    public void testIsLocked() {
        RLock lock = redisson.getLock("lock");
        Assert.assertFalse(lock.isLocked());
        lock.lock();
        Assert.assertTrue(lock.isLocked());
        lock.unlock();
        Assert.assertFalse(lock.isLocked());
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockFail() throws InterruptedException {
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
        Assert.assertTrue(lock.tryLock());
        Assert.assertTrue(lock.tryLock());
        lock.unlock();
        // next row  for test renew expiration tisk.
        //Thread.currentThread().sleep(TimeUnit.SECONDS.toMillis(RedissonLock.LOCK_EXPIRATION_INTERVAL_SECONDS*2));
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                RLock lock1 = redisson.getLock("lock1");
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
            Lock lock = r.getLock("testConcurrency_SingleInstance");
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

        Assert.assertEquals(16 * iterations, lockedCounter.get());
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

        Assert.assertEquals(iterations, lockedCounter.get());
    }

}
