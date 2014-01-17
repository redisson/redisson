package org.redisson;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.core.RLock;

public class RedissonLockTest extends BaseConcurrentTest {

    Redisson redisson = Redisson.create();

    @Before
    public void before() {
        redisson = Redisson.create();
    }

    @After
    public void after() {
        redisson.shutdown();
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

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assert.assertFalse(lock.isHeldByCurrentThread());
                latch.countDown();
            };
        }.start();

        latch.await();
        lock.unlock();

        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assert.assertFalse(lock.isHeldByCurrentThread());
                latch2.countDown();
            };
        }.start();

        latch2.await();
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

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assert.assertTrue(lock.isLocked());
                latch.countDown();
            };
        }.start();

        latch.await();
        lock.unlock();

        final CountDownLatch latch2 = new CountDownLatch(1);
        new Thread() {
            public void run() {
                RLock lock = redisson.getLock("lock");
                Assert.assertFalse(lock.isLocked());
                latch2.countDown();
            };
        }.start();

        latch2.await();
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
    public void testUnlockFail() {
        Lock lock = redisson.getLock("lock1");
        lock.unlock();
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
    public void testReentrancy() {
        Lock lock = redisson.getLock("lock1");
        lock.lock();
        lock.lock();
        lock.unlock();
        lock.unlock();
    }


    @Test
    public void testConcurrency_SingleInstance() throws InterruptedException {
        final AtomicInteger lockedCounter = new AtomicInteger();

        int iterations = 100;
        testSingleInstanceConcurrency(iterations, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                Lock lock = redisson.getLock("testConcurrency_SingleInstance");
                lock.lock();
                lockedCounter.set(lockedCounter.get() + 1);
                lock.unlock();
            }
        });

        Assert.assertEquals(iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrencyLoop_MultiInstance() throws InterruptedException {
        final int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(16, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                for (int i = 0; i < iterations; i++) {
                    redisson.getLock("testConcurrency_MultiInstance").lock();
                    lockedCounter.set(lockedCounter.get() + 1);
                    redisson.getLock("testConcurrency_MultiInstance").unlock();
                }
            }
        });

        Assert.assertEquals(16 * iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrency_MultiInstance() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        testMultiInstanceConcurrency(iterations, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                Lock lock = redisson.getLock("testConcurrency_MultiInstance");
                lock.lock();
                lockedCounter.set(lockedCounter.get() + 1);
                lock.unlock();
            }
        });

        Assert.assertEquals(iterations, lockedCounter.get());
    }

}
