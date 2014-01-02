package org.redisson;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.RedissonMapTest.SimpleKey;
import org.redisson.RedissonMapTest.SimpleValue;

public class RedissonConcurrentMapTest {

    @Test
    public void testSinglePutIfAbsent_SingleInstance() throws InterruptedException {
        final String name = "testSinglePutIfAbsent_SingleInstance";

        ConcurrentMap<String, String> map = Redisson.create().getMap(name);
        map.putIfAbsent("1", "0");
        testSingleInstanceConcurrency(100, new SingleInstanceRunnable() {
            @Override
            public void run(Redisson redisson) {
                ConcurrentMap<String, String> map = redisson.getMap(name);
                map.putIfAbsent("1", "1");
            }
        });

        ConcurrentMap<String, String> testMap = Redisson.create().getMap(name);
        Assert.assertEquals("0", testMap.get("1"));

        assertMapSize(1, name);
    }

    @Test
    public void testMultiPutIfAbsent_SingleInstance() throws InterruptedException {
        final String name = "testMultiPutIfAbsent_SingleInstance";
        testSingleInstanceConcurrency(100, new SingleInstanceRunnable() {
            @Override
            public void run(Redisson redisson) {
                ConcurrentMap<String, String> map = redisson.getMap(name);
                map.putIfAbsent("" + Math.random(), "1");
            }
        });

        assertMapSize(100, name);
    }

    @Test
    public void testMultiPutIfAbsent_MultiInstance() throws InterruptedException {
        final String name = "testMultiPutIfAbsent_MultiInstance";
        testMultiInstanceConcurrency(100, new Runnable() {
            @Override
            public void run() {
                ConcurrentMap<String, String> map = Redisson.create().getMap(name);
                map.putIfAbsent("" + Math.random(), "1");
            }
        });

        assertMapSize(100, name);
    }

    private void testMultiInstanceConcurrency(int iterations, final Runnable runnable) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        long watch = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            executor.execute(runnable);
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

        System.out.println(System.currentTimeMillis() - watch);
    }

    private void testSingleInstanceConcurrency(int iterations, final SingleInstanceRunnable singleInstaneRunnable) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        final Redisson redisson = Redisson.create();
        long watch = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    singleInstaneRunnable.run(redisson);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

        System.out.println(System.currentTimeMillis() - watch);
    }

    private void assertMapSize(int size, String name) {
        Map<String, String> map = Redisson.create().getMap(name);
        Assert.assertEquals(size, map.size());
        clear(map);
    }

    @Test
    public void testMultiPut_SingleInstance() throws InterruptedException {
        final String name = "testMultiPut_SingleInstance";
        testSingleInstanceConcurrency(100, new SingleInstanceRunnable() {
            @Override
            public void run(Redisson redisson) {
                Map<String, String> map = redisson.getMap(name);
                map.put("" + Math.random(), "1");
            }
        });

        assertMapSize(100, name);
    }


    @Test
    public void testMultiPut_MultiInstance() throws InterruptedException {
        final String name = "testMultiPut_MultiInstance";
        testMultiInstanceConcurrency(100, new Runnable() {
            @Override
            public void run() {
                ConcurrentMap<String, String> map = Redisson.create().getMap(name);
                map.putIfAbsent("" + Math.random(), "1");
            }
        });

        assertMapSize(100, name);
    }

    private void clear(Map<?, ?> map) {
        map.clear();
        Assert.assertEquals(0, map.size());
    }

}
