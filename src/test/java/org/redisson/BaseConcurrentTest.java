package org.redisson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.redisson.client.RedisClient;

public abstract class BaseConcurrentTest extends BaseTest {

    protected void testMultiInstanceConcurrency(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        System.out.println("Multi Instance Concurrent Job Interation: " + iterations);
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);
        final Map<Integer, RedissonClient> instances = new HashMap<>();

        pool.submit(() -> {
            IntStream.range(0, iterations)
                    .parallel()
                    .forEach((i) -> instances.put(i, BaseTest.createInstance()));
        });

        long watch = System.currentTimeMillis();
        pool.awaitQuiescence(5, TimeUnit.MINUTES);

        pool.submit(() -> {
            IntStream.range(0, iterations)
                    .parallel()
                    .forEach((i) -> runnable.run(instances.get(i)));
        });

        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(RedissonRuntimeEnvironment.isTravis ? 10 : 3, TimeUnit.MINUTES));

        System.out.println("multi: " + (System.currentTimeMillis() - watch));

        pool = new ForkJoinPool();

        pool.submit(() -> {
            instances.values()
                    .parallelStream()
                    .<RedisClient>forEach((r) -> r.shutdown());
        });

        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(5, TimeUnit.MINUTES));
    }

    protected void testMultiInstanceConcurrencySequentiallyLaunched(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        System.out.println("Multi Instance Concurrent Job Interation: " + iterations);
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        final Map<Integer, RedissonClient> instances = new HashMap<Integer, RedissonClient>();
        for (int i = 0; i < iterations; i++) {
            instances.put(i, BaseTest.createInstance());
        }

        long watch = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            final int n = i;
            executor.execute(() -> runnable.run(instances.get(n)));
        }

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(5, TimeUnit.MINUTES));

        System.out.println("multi: " + (System.currentTimeMillis() - watch));

        executor = Executors.newCachedThreadPool();

        for (final RedissonClient redisson : instances.values()) {
            executor.execute(() -> redisson.shutdown());
        }

        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(5, TimeUnit.MINUTES));
    }

    protected void testSingleInstanceConcurrency(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        System.out.println("Single Instance Concurrent Job Interation: " + iterations);
        final RedissonClient r = BaseTest.createInstance();
        long watch = System.currentTimeMillis();

//        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        for (int i = 0; i < iterations; i++) {
            pool.execute(() -> {
                runnable.run(r);
            });
        }
//        pool.submit(() -> {
//            IntStream.range(0, iterations)
//                    .parallel()
//                    .forEach((i) -> {
//                        runnable.run(r);
//                    });
//        });

        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(RedissonRuntimeEnvironment.isTravis ? 10 : 3, TimeUnit.MINUTES));

        System.out.println(System.currentTimeMillis() - watch);

        r.shutdown();
    }

}
