package org.redisson;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RCountDownLatch;

public class RedissonCountDownLatchConcurrentTest {

    @Test
    public void testSingleCountDownAwait_SingleInstance() throws InterruptedException {
        final int iterations = Runtime.getRuntime().availableProcessors()*3;

        Redisson redisson = BaseTest.createInstance();
        final RCountDownLatch latch = redisson.getCountDownLatch("latch");
        latch.trySetCount(iterations);

        final AtomicInteger counter = new AtomicInteger();
        ExecutorService executor = Executors.newScheduledThreadPool(iterations);
        for (int i = 0; i < iterations; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        latch.await();
                        Assert.assertEquals(0, latch.getCount());
                        Assert.assertEquals(iterations, counter.get());
                    } catch (InterruptedException e) {
                        Assert.fail();
                    }
                }
            });
        }

        ExecutorService countDownExecutor = Executors.newFixedThreadPool(iterations);
        for (int i = 0; i < iterations; i++) {
            countDownExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    counter.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        redisson.shutdown();
    }

}
