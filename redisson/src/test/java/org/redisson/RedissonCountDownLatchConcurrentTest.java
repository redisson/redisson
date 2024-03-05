package org.redisson;

import org.junit.jupiter.api.*;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RedissonClient;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RedissonCountDownLatchConcurrentTest extends RedisDockerTest {
    
    @Test
    public void testSingleCountDownAwait_SingleInstance() throws InterruptedException {
        int iterations = Runtime.getRuntime().availableProcessors()*3;

        RCountDownLatch latch = redisson.getCountDownLatch("latch");
        latch.trySetCount(iterations);

        AtomicInteger counter = new AtomicInteger();
        ExecutorService executor = Executors.newScheduledThreadPool(iterations);
        for (int i = 0; i < iterations; i++) {
            executor.execute(() -> {
                try {
                    latch.await();
                    Assertions.assertEquals(0, latch.getCount());
                    Assertions.assertEquals(iterations, counter.get());
                } catch (InterruptedException e) {
                    Assertions.fail();
                }
            });
        }

        ExecutorService countDownExecutor = Executors.newFixedThreadPool(iterations);
        for (int i = 0; i < iterations; i++) {
            countDownExecutor.execute(() -> {
                latch.countDown();
                counter.incrementAndGet();
            });
        }

        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

}
