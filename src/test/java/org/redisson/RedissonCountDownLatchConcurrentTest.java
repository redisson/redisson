package org.redisson;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RCountDownLatch;

public class RedissonCountDownLatchConcurrentTest {

    @Test
    public void testSingleCountDownAwait_SingleInstance() throws InterruptedException {
        int iterations = Runtime.getRuntime().availableProcessors()*2;

        Redisson redisson = Redisson.create();
        final RCountDownLatch latch = redisson.getCountDownLatch("latch");
        latch.trySetCount(iterations);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(iterations);
        for (int i = 0; i < iterations; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Assert.fail();
                    }
                    latch.countDown();
                }
            });
        }

        executor = Executors.newScheduledThreadPool(iterations);
        for (int i = 0; i < iterations; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Assert.fail();
                    }
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        redisson.shutdown();
    }

}
