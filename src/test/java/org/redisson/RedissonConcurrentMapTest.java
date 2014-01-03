package org.redisson;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.junit.Assert;
import org.junit.Test;

public class RedissonConcurrentMapTest extends RedissonConcurrentTest {

    @Test
    public void testSingleReplaceOldValue_SingleInstance() throws InterruptedException {
        final String name = "testSingleReplaceOldValue_SingleInstance";

        ConcurrentMap<String, String> map = Redisson.create().getMap(name);
        map.put("1", "122");

        testSingleInstanceConcurrency(100, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                ConcurrentMap<String, String> map = redisson.getMap(name);
                map.replace("1", "122", "32");
                map.replace("1", "0", "31");
            }
        });

        ConcurrentMap<String, String> testMap = Redisson.create().getMap(name);
        Assert.assertEquals("32", testMap.get("1"));

        assertMapSize(1, name);
    }

    @Test
    public void testSingleReplace_SingleInstance() throws InterruptedException {
        final String name = "testSingleReplace_SingleInstance";

        ConcurrentMap<String, String> map = Redisson.create().getMap(name);
        map.put("1", "0");

        testSingleInstanceConcurrency(100, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                ConcurrentMap<String, String> map = redisson.getMap(name);
                map.replace("1", "3");
            }
        });

        ConcurrentMap<String, String> testMap = Redisson.create().getMap(name);
        Assert.assertEquals("3", testMap.get("1"));

        assertMapSize(1, name);
    }

    @Test
    public void testSingleRemoveValue_SingleInstance() throws InterruptedException {
        final String name = "testSingleRemoveValue_SingleInstance";

        ConcurrentMap<String, String> map = Redisson.create().getMap(name);
        map.putIfAbsent("1", "0");
        testSingleInstanceConcurrency(100, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                ConcurrentMap<String, String> map = redisson.getMap(name);
                map.remove("1", "0");
            }
        });

        assertMapSize(0, name);
    }

    @Test
    public void testSinglePutIfAbsent_SingleInstance() throws InterruptedException {
        final String name = "testSinglePutIfAbsent_SingleInstance";

        ConcurrentMap<String, String> map = Redisson.create().getMap(name);
        map.putIfAbsent("1", "0");
        testSingleInstanceConcurrency(100, new RedissonRunnable() {
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
        testSingleInstanceConcurrency(100, new RedissonRunnable() {
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
        testMultiInstanceConcurrency(100, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                ConcurrentMap<String, String> map = redisson.getMap(name);
                map.putIfAbsent("" + Math.random(), "1");
            }
        });

        assertMapSize(100, name);
    }

    private void assertMapSize(int size, String name) {
        Map<String, String> map = Redisson.create().getMap(name);
        Assert.assertEquals(size, map.size());
        clear(map);
    }

    @Test
    public void testMultiPut_SingleInstance() throws InterruptedException {
        final String name = "testMultiPut_SingleInstance";
        testSingleInstanceConcurrency(100, new RedissonRunnable() {
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
        testMultiInstanceConcurrency(100, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                ConcurrentMap<String, String> map = redisson.getMap(name);
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
