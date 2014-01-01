package org.redisson;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import org.junit.Assert;
import org.junit.Test;

public class RedissonLockTest {

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
    public void testConcurrencySingleRedisson() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();
        final Redisson redisson = Redisson.create();

        for (int i = 0; i < iterations; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Lock lock = redisson.getLock("lock1");
                    lock.lock();
                    lockedCounter.set(lockedCounter.get() + 1);
                    lock.unlock();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.MINUTES);

        Assert.assertEquals(iterations, lockedCounter.get());
    }

    @Test
    public void testConcurrencyMultiRedisson() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        for (int i = 0; i < iterations; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Redisson redisson = Redisson.create();
                    Lock lock = redisson.getLock("lock1");
                    lock.lock();
                    lockedCounter.set(lockedCounter.get() + 1);
                    lock.unlock();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.MINUTES);

        Assert.assertEquals(iterations, lockedCounter.get());
    }

}
