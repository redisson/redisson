package org.redisson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import io.netty.channel.nio.NioEventLoopGroup;

public abstract class BaseConcurrentTest extends RedisDockerTest {

    protected void testMultiInstanceConcurrency(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*2);

        NioEventLoopGroup group = new NioEventLoopGroup();
        final Map<Integer, RedissonClient> instances = new HashMap<Integer, RedissonClient>();
        for (int i = 0; i < iterations; i++) {
            Config config = createConfig();
            config.setEventLoopGroup(group);
            RedissonClient instance = Redisson.create(config);
            instances.put(i, instance);
        }

        long watch = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            final int n = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    RedissonClient redisson = instances.get(n);
                    runnable.run(redisson);
                }
            });
        }

        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(3, TimeUnit.MINUTES));

        System.out.println("multi: " + (System.currentTimeMillis() - watch));

        executor = Executors.newCachedThreadPool();

        for (final RedissonClient redisson : instances.values()) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    redisson.shutdown();
                }
            });
        }

        group.shutdownGracefully();
        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(5, TimeUnit.MINUTES));
    }

    protected void testSingleInstanceConcurrency(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        System.out.println("Single Instance Concurrent Job Interation: " + iterations);
        final RedissonClient r = createInstance();
        long watch = System.currentTimeMillis();

        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        for (int i = 0; i < iterations; i++) {
            pool.execute(() -> {
                runnable.run(r);
            });
        }

        pool.shutdown();
        Assertions.assertTrue(pool.awaitTermination(3, TimeUnit.MINUTES));

        System.out.println(System.currentTimeMillis() - watch);

        r.shutdown();
    }

}
