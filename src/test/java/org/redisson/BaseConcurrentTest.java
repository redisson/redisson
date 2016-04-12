package org.redisson;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.redisson.client.RedisClient;

public abstract class BaseConcurrentTest extends BaseTest {

    protected void testMultiInstanceConcurrency(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        System.out.println("Multi Instance Concurrent Job Interation:" + iterations);
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
            final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            final AtomicLong u = new AtomicLong(runtimeBean.getUptime());
            IntStream.range(0, iterations)
                    .parallel()
                    .forEach((i) -> {
                        if (RedissonRuntimeEnvironment.isTravis) {
                            long upTime = runtimeBean.getUptime();
                            if (upTime >= u.get() + 10000) {
                                u.set(upTime);
                                System.out.printf("Test Up Time    = %.3f (s)%n", upTime / 1000d);
                                System.out.printf("Heap Usage      = %.3f (MB)%n", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1024d / 1024d);
                                System.out.printf("None Heap Usage = %.3f (MB)%n", ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() / 1024d / 1024d);
                                System.out.println("=============================");
                            }
                        }
                        runnable.run(instances.get(i));
                    });
            System.out.printf("Test Up Time    = %.3f (s)%n", runtimeBean.getUptime() /1000d);
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

    protected void testSingleInstanceConcurrency(int iterations, final RedissonRunnable runnable) throws InterruptedException {
        System.out.println("Single Instance Concurrent Job Interation:" + iterations);
        final RedissonClient r = BaseTest.createInstance();
        long watch = System.currentTimeMillis();

        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);

        pool.submit(() -> {
            final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            final AtomicLong u = new AtomicLong(runtimeBean.getUptime());
            IntStream.range(0, iterations)
                    .parallel()
                    .forEach((i) -> {
                        long upTime = runtimeBean.getUptime();
                        if (upTime >= u.get() + 10000) {
                            u.set(upTime);
                            System.out.printf("Test Up Time    = %.3f (s)%n", upTime / 1000d);
                            System.out.printf("Heap Usage      = %.3f (MB)%n", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / 1024d / 1024d);
                            System.out.printf("None Heap Usage = %.3f (MB)%n", ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed() / 1024d / 1024d);
                            System.out.println("=============================");
                        }
                        runnable.run(r);
                    });
            System.out.printf("Test Up Time    = %.3f (s)%n", runtimeBean.getUptime() / 1000d);
        });

        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(RedissonRuntimeEnvironment.isTravis ? 10 : 3, TimeUnit.MINUTES));

        System.out.println(System.currentTimeMillis() - watch);

        r.shutdown();
    }

}
