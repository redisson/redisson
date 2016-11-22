package org.redisson.rule;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.reactivestreams.Publisher;
import org.redisson.RedissonRunnable;
import org.redisson.RedissonRuntimeEnvironment;
import org.redisson.api.RCollectionReactive;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import io.netty.channel.nio.NioEventLoopGroup;
import reactor.rx.Promise;
import reactor.rx.Streams;

/**
 * @author Philipp Marx
 */
public class TestUtil {

    public static <V> V sync(Publisher<V> ob) {
        Promise<V> promise;
        if (Promise.class.isAssignableFrom(ob.getClass())) {
            promise = (Promise<V>) ob;
        } else {
            promise = Streams.wrap(ob).next();
        }

        V val = promise.poll();
        if (promise.isError()) {
            throw new RuntimeException(promise.reason());
        }
        return val;
    }

    public static <V> Iterable<V> sync(RCollectionReactive<V> list) {
        return Streams.create(list.iterator()).toList().poll();
    }

    public static <V> Iterable<V> sync(RScoredSortedSetReactive<V> list) {
        return Streams.create(list.iterator()).toList().poll();
    }

    public static void testMultiInstanceConcurrency(RedissonRule redissonRule,
                                                    int iterations,
                                                    final RedissonRunnable runnable) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        NioEventLoopGroup group = new NioEventLoopGroup();
        final Map<Integer, RedissonClient> instances = new HashMap<Integer, RedissonClient>();
        for (int i = 0; i < iterations; i++) {
            Config config = redissonRule.getSharedConfig();
            config.setEventLoopGroup(group);
            RedissonClient instance = redissonRule.createClient(config);
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
        Assert.assertTrue(executor.awaitTermination(RedissonRuntimeEnvironment.isTravis ? 10 : 3, TimeUnit.MINUTES));

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
        Assert.assertTrue(executor.awaitTermination(5, TimeUnit.MINUTES));
    }

    public static void testMultiInstanceConcurrencySequentiallyLaunched(RedissonRule redissonRule,
                                                                        int iterations,
                                                                        final RedissonRunnable runnable)
            throws InterruptedException {
        System.out.println("Multi Instance Concurrent Job Interation: " + iterations);
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        final Map<Integer, RedissonClient> instances = new HashMap<Integer, RedissonClient>();
        for (int i = 0; i < iterations; i++) {
            instances.put(i, redissonRule.createClient());
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

    public static void testSingleInstanceConcurrency(RedissonRule redissonRule, int iterations, final RedissonRunnable runnable)
            throws InterruptedException {
        System.out.println("Single Instance Concurrent Job Interation: " + iterations);
        final RedissonClient r = redissonRule.createClient();
        long watch = System.currentTimeMillis();

        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        for (int i = 0; i < iterations; i++) {
            pool.execute(() -> {
                runnable.run(r);
            });
        }

        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(RedissonRuntimeEnvironment.isTravis ? 20 : 3, TimeUnit.MINUTES));

        System.out.println(System.currentTimeMillis() - watch);

        r.shutdown();
    }

    public static <V> Iterable<V> toIterable(Publisher<V> pub) {
        return Streams.create(pub).toList().poll();
    }

    public static <V> Iterator<V> toIterator(Publisher<V> pub) {
        return Streams.create(pub).toList().poll().iterator();
    }
}
