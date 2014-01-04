package org.redisson;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import org.junit.Assert;
import org.junit.Test;

public class RedissonLockTest extends BaseConcurrentTest {

    @Test
    public void testLockUnlock() {
        Redisson redisson = Redisson.create();
        Lock lock = redisson.getLock("lock1");
        lock.lock();
        lock.unlock();

        lock.lock();
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
