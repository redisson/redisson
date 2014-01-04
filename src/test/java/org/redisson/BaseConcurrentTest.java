package org.redisson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class BaseConcurrentTest {

    protected void testMultiInstanceConcurrency(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        final List<Redisson> redissons = new ArrayList<Redisson>();
        long watch = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Redisson redisson = Redisson.create();
                    redissons.add(redisson);
                    runnable.run(redisson);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

        System.out.println("multi: " + (System.currentTimeMillis() - watch));

        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (final Redisson redisson : redissons) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    redisson.shutdown();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

    }

    protected void testSingleInstanceConcurrency(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        final Redisson redisson = Redisson.create();
        long watch = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    runnable.run(redisson);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

        System.out.println(System.currentTimeMillis() - watch);

        redisson.shutdown();
    }



}
