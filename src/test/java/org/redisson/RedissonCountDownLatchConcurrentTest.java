package org.redisson;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.core.RCountDownLatch;

public class RedissonCountDownLatchConcurrentTest {
    
    @Before
    public void before() throws IOException, InterruptedException {
        RedisRunner.startDefaultRedisTestInstance();
    }

    @After
    public void after() throws InterruptedException {
        RedisRunner.shutDownDefaultRedisTestInstance();
    }

    @Test
    public void testSingleCountDownAwait_SingleInstance() throws InterruptedException {
        final int iterations = Runtime.getRuntime().availableProcessors()*3;

        RedissonClient redisson = BaseTest.createInstance();
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
        Assert.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        redisson.shutdown();
    }

}
