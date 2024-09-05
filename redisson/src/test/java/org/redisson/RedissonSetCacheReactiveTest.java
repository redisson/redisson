package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import reactor.core.publisher.Mono;

public class RedissonSetCacheReactiveTest extends BaseReactiveTest {

    public static class SimpleBean implements Serializable {

        private Long lng;

        public Long getLng() {
            return lng;
        }

        public void setLng(Long lng) {
            this.lng = lng;
        }

    }

    @Test
    public void testBatchScriptCache() throws InterruptedException {
        Config config = new Config();
        config.setUseScriptCache(true);
        config.useSingleServer()
                .setAddress(redisson.getConfig().useSingleServer().getAddress());
        RedissonReactiveClient client = Redisson.create(config).reactive();

        RBatchReactive batch = client.createBatch();
        Mono<Boolean> setResult = batch.getSetCache("test2",
                        StringCodec.INSTANCE)
                .add("setValue", 10, TimeUnit.SECONDS);

        Thread.sleep(400);

        Mono<Integer> monoMsSetSize = batch.getSetCache("test2",
                StringCodec.INSTANCE).size();
        batch.execute().subscribe();
        Integer v = Mono.zip(setResult, monoMsSetSize).flatMap(touple -> {
            return Mono.just(touple.getT2());
        }).block();

        assertThat(v).isEqualTo(1);

        client.shutdown();
    }

    @Test
    public void testAddBean() throws InterruptedException, ExecutionException {
        SimpleBean sb = new SimpleBean();
        sb.setLng(1L);
        RSetCacheReactive<SimpleBean> set = redisson.getSetCache("simple");
        sync(set.add(sb));
        Assertions.assertEquals(sb.getLng(), toIterator(set.iterator()).next().getLng());
    }

    @Test
    public void testAddExpire() throws InterruptedException, ExecutionException {
        RSetCacheReactive<String> set = redisson.getSetCache("simple3");
        sync(set.add("123", 1, TimeUnit.SECONDS));
        assertThat(sync(set)).containsOnly("123");

        Thread.sleep(1000);

        Assertions.assertFalse(sync(set.contains("123")));
    }

    @Test
    public void testAddExpireTwise() throws InterruptedException, ExecutionException {
        RSetCacheReactive<String> set = redisson.getSetCache("simple31");
        sync(set.add("123", 1, TimeUnit.SECONDS));
        Thread.sleep(1000);

        Assertions.assertFalse(sync(set.contains("123")));

        sync(set.add("4341", 1, TimeUnit.SECONDS));
        Thread.sleep(1000);

        Assertions.assertFalse(sync(set.contains("4341")));
    }

    @Test
    public void testExpireOverwrite() throws InterruptedException, ExecutionException {
        RSetCacheReactive<String> set = redisson.getSetCache("simple");
        assertThat(sync(set.add("123", 1, TimeUnit.SECONDS))).isTrue();

        Thread.sleep(800);

        assertThat(sync(set.add("123", 1, TimeUnit.SECONDS))).isFalse();

        Thread.sleep(800);
        assertThat(sync(set.contains("123"))).isTrue();

        Thread.sleep(250);

        assertThat(sync(set.contains("123"))).isFalse();
    }

    @Test
    public void testRemove() throws InterruptedException, ExecutionException {
        RSetCacheReactive<Integer> set = redisson.getSetCache("simple");
        sync(set.add(1, 1, TimeUnit.SECONDS));
        sync(set.add(3, 2, TimeUnit.SECONDS));
        sync(set.add(7, 3, TimeUnit.SECONDS));

        Assertions.assertTrue(sync(set.remove(1)));
        Assertions.assertFalse(sync(set.contains(1)));
        assertThat(sync(set)).contains(3, 7);

        Assertions.assertFalse(sync(set.remove(1)));
        assertThat(sync(set)).contains(3, 7);

        Assertions.assertTrue(sync(set.remove(3)));
        Assertions.assertFalse(sync(set.contains(3)));
        assertThat(sync(set)).contains(7);
        Assertions.assertEquals(1, sync(set.size()).intValue());
    }

    @Test
    public void testIteratorSequence() throws InterruptedException {
        RSetCacheReactive<Long> set = redisson.getSetCache("set");
        for (int i = 0; i < 1000; i++) {
            sync(set.add(Long.valueOf(i)));
        }

        Thread.sleep(1000);
        assertThat(sync(set.size())).isEqualTo(1000);
        
        Set<Long> setCopy = new HashSet<Long>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Long.valueOf(i));
        }

        checkIterator(set, setCopy);
    }

    private void checkIterator(RSetCacheReactive<Long> set, Set<Long> setCopy) {
        for (Iterator<Long> iterator = toIterator(set.iterator()); iterator.hasNext();) {
            Long value = iterator.next();
            if (!setCopy.remove(value)) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, setCopy.size());
    }

    @Test
    public void testRetainAll() {
        RSetCacheReactive<Integer> set = redisson.getSetCache("set");
        for (int i = 0; i < 10000; i++) {
            sync(set.add(i));
            sync(set.add(i*10, 10, TimeUnit.SECONDS));
        }

        Assertions.assertTrue(sync(set.retainAll(Arrays.asList(1, 2))));
        assertThat(sync(set)).contains(1, 2);
        Assertions.assertEquals(2, sync(set.size()).intValue());
    }

    @Test
    public void testContainsAll() {
        RSetCacheReactive<Integer> set = redisson.getSetCache("set");
        for (int i = 0; i < 200; i++) {
            sync(set.add(i));
        }

        Assertions.assertTrue(sync(set.containsAll(Collections.emptyList())));
        Assertions.assertTrue(sync(set.containsAll(Arrays.asList(30, 11))));
        Assertions.assertFalse(sync(set.containsAll(Arrays.asList(30, 711, 11))));
    }

    @Test
    public void testContains() throws InterruptedException {
        RSetCacheReactive<TestObject> set = redisson.getSetCache("set");

        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("2", "3"), 1, TimeUnit.SECONDS));
        sync(set.add(new TestObject("3", "4")));
        sync(set.add(new TestObject("5", "6")));

        Thread.sleep(1000);

        Assertions.assertFalse(sync(set.contains(new TestObject("2", "3"))));
        Assertions.assertTrue(sync(set.contains(new TestObject("1", "2"))));
        Assertions.assertFalse(sync(set.contains(new TestObject("1", "9"))));
    }

    @Test
    public void testDuplicates() {
        RSetCacheReactive<TestObject> set = redisson.getSetCache("set");

        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("2", "3")));
        sync(set.add(new TestObject("3", "4")));
        sync(set.add(new TestObject("5", "6")));

        Assertions.assertEquals(4, sync(set.size()).intValue());
    }

    @Test
    public void testSize() {
        RSetCacheReactive<Integer> set = redisson.getSetCache("set");
        Assertions.assertEquals(true, sync(set.add(1)));
        Assertions.assertEquals(true, sync(set.add(2)));
        Assertions.assertEquals(true, sync(set.add(3)));
        Assertions.assertEquals(false, sync(set.add(3)));
        Assertions.assertEquals(false, sync(set.add(3)));
        Assertions.assertEquals(true, sync(set.add(4)));
        Assertions.assertEquals(true, sync(set.add(5)));
        Assertions.assertEquals(false, sync(set.add(5)));

        Assertions.assertEquals(5, sync(set.size()).intValue());
    }


    @Test
    public void testRetainAllEmpty() {
        RSetCacheReactive<Integer> set = redisson.getSetCache("set");
        sync(set.add(1));
        sync(set.add(2));
        sync(set.add(3));
        sync(set.add(4));
        sync(set.add(5));

        Assertions.assertTrue(sync(set.retainAll(Collections.<Integer>emptyList())));
        Assertions.assertEquals(0, sync(set.size()).intValue());
    }

    @Test
    public void testRetainAllNoModify() {
        RSetCacheReactive<Integer> set = redisson.getSetCache("set");
        sync(set.add(1));
        sync(set.add(2));

        Assertions.assertFalse(sync(set.retainAll(Arrays.asList(1, 2)))); // nothing changed
        assertThat(sync(set)).contains(1, 2);
    }

    @Test
    public void testExpiredIterator() throws InterruptedException {
        RSetCacheReactive<String> cache = redisson.getSetCache("simple");
        sync(cache.add("0"));
        sync(cache.add("1", 1, TimeUnit.SECONDS));
        sync(cache.add("2", 3, TimeUnit.SECONDS));
        sync(cache.add("3", 4, TimeUnit.SECONDS));
        sync(cache.add("4", 1, TimeUnit.SECONDS));

        Thread.sleep(1000);

        assertThat(sync(cache)).contains("0", "2", "3");
    }

    @Test
    public void testExpire() throws InterruptedException {
        RSetCacheReactive<String> cache = redisson.getSetCache("simple");
        sync(cache.add("8", 1, TimeUnit.SECONDS));

        sync(cache.expire(100, TimeUnit.MILLISECONDS));

        Thread.sleep(500);

        Assertions.assertEquals(0, sync(cache.size()).intValue());
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RSetCacheReactive<String> cache = redisson.getSetCache("simple");
        sync(cache.add("8", 1, TimeUnit.SECONDS));

        sync(cache.expireAt(System.currentTimeMillis() + 100));

        Thread.sleep(500);

        Assertions.assertEquals(0, sync(cache.size()).intValue());
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RSetCacheReactive<String> cache = redisson.getSetCache("simple");
        sync(cache.add("8", 1, TimeUnit.SECONDS));

        sync(cache.expireAt(System.currentTimeMillis() + 100));

        sync(cache.clearExpire());

        Thread.sleep(500);

        Assertions.assertEquals(1, sync(cache.size()).intValue());
    }

    @Test
    public void testScheduler() throws InterruptedException {
        RSetCacheReactive<String> cache = redisson.getSetCache("simple33");
        Assertions.assertFalse(sync(cache.contains("33")));

        Assertions.assertTrue(sync(cache.add("33", 5, TimeUnit.SECONDS)));

        Thread.sleep(11000);

        Assertions.assertEquals(0, sync(cache.size()).intValue());

    }

    @Test
    public void testAddIfAbsentWithMapParam() throws InterruptedException {
        sync(redisson.getKeys().flushall());
        RSetCacheReactive<String> cache = redisson.getSetCache("cache");
        Map<String, Duration> map = new HashMap<>();
        map.put("key1", Duration.ofMinutes(1));
        map.put("key2", Duration.ofMinutes(1));
        assertThat(sync(cache.addIfAbsent(map))).isTrue();
        map = new HashMap<>();
        map.put("key1", Duration.ofMinutes(1));
        assertThat(sync(cache.addIfAbsent(map))).isFalse();
        map = new HashMap<>();
        map.put("key3", Duration.ofSeconds(1));
        assertThat(sync(cache.addIfAbsent(map))).isTrue();
        Thread.sleep(1200);
        assertThat(sync(cache.addIfAbsent(map))).isTrue();
        sync((redisson.getKeys().flushall()));
    }

}
