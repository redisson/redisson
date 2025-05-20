package org.redisson;

import org.awaitility.Awaitility;
import org.joor.Reflect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSetCache;
import org.redisson.api.listener.SetAddListener;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.eviction.EvictionScheduler;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSetCacheTest extends RedisDockerTest {

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
    public void testAddIfAbsent() throws InterruptedException {
        Map<String, Duration> map = new HashMap<>();
        map.put("200", Duration.ofSeconds(2));
        RSetCache<String> test = redisson.getSetCache("test");
        assertThat(test.addAllIfAbsent(map)).isEqualTo(1);
        assertThat(test.addAllIfAbsent(map)).isZero();
        assertThat(test.contains("200")).isTrue();

        assertThat(test.addIfAbsent(Duration.ofSeconds(2), "100")).isTrue();
        assertThat(test.addIfAbsent(Duration.ofSeconds(2), "100")).isFalse();
        assertThat(test.contains("100")).isTrue();

        Thread.sleep(2000);

        assertThat(test.contains("100")).isFalse();
        assertThat(test.contains("200")).isFalse();

        assertThat(test.addIfAbsent(Duration.ofSeconds(2), "100")).isTrue();
        assertThat(test.addAllIfAbsent(map)).isEqualTo(1);
    }

    @Test
    public void testAddIfExists() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("list");
        cache.add("a", 1, TimeUnit.SECONDS);
        assertThat(cache.addIfExists(Duration.ofSeconds(2), "a")).isTrue();

        Thread.sleep(1500);
        assertThat(cache.contains("a")).isTrue();
        Thread.sleep(700);
        assertThat(cache.contains("a")).isFalse();
    }

    @Test
    public void testAddAll() {
        RSetCache<String> cache = redisson.getSetCache("list");
        cache.add("a", 1, TimeUnit.SECONDS);
        Map<String, Duration> map = new HashMap<>();
        map.put("a", Duration.ofSeconds(2));
        map.put("b", Duration.ofSeconds(2));
        map.put("c", Duration.ofSeconds(2));
        assertThat(cache.addAll(map)).isEqualTo(2);
        assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    public void testAddAllIfExists() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("list");
        Map<String, Duration> map = new HashMap<>();
        map.put("a", Duration.ofSeconds(2));
        map.put("b", Duration.ofSeconds(2));
        map.put("c", Duration.ofSeconds(2));
        assertThat(cache.addAllIfExist(map)).isZero();
        assertThat(cache.size()).isZero();

        cache.add("a", 1, TimeUnit.SECONDS);
        cache.add("b", 1, TimeUnit.SECONDS);
        assertThat(cache.addAllIfExist(map)).isEqualTo(2);
        assertThat(cache.contains("c")).isFalse();

        Thread.sleep(1500);
        assertThat(cache.contains("a")).isTrue();
        assertThat(cache.contains("b")).isTrue();
        Thread.sleep(700);
        assertThat(cache.contains("a")).isFalse();
        assertThat(cache.contains("b")).isFalse();
    }

    @Test
    public void testTryAdd() {
        RSetCache<String> cache = redisson.getSetCache("list", IntegerCodec.INSTANCE);
        Set<String> names = new HashSet<>();
        int elements = 200000;
        for (int i = 0; i < elements; i++) {
            names.add("name" + i);
        }

        boolean s = cache.tryAdd(names.toArray(new String[]{}));
        assertThat(s).isTrue();
        assertThat(cache.size()).isEqualTo(elements);

        Set<String> names2 = new HashSet<>();
        for (int i = elements+1; i < elements + 10000; i++) {
            names2.add("name" + i);
        }
        names2.add("name10");

        boolean r = cache.tryAdd(names2.toArray(new String[]{}));
        assertThat(r).isFalse();
        assertThat(cache.size()).isEqualTo(elements);
    }

    @Test
    public void testDestroy() {
        RSetCache<String> cache = redisson.getSetCache("test");
        
        EvictionScheduler evictionScheduler = ((Redisson)redisson).getEvictionScheduler();
        Map<?, ?> map = Reflect.on(evictionScheduler).get("tasks");
        assertThat(map.isEmpty()).isFalse();
        cache.destroy();
        assertThat(map.isEmpty()).isTrue();
    }
    
    @Test
    public void testRemoveAll() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        set.add(1);
        set.add(2, 10, TimeUnit.SECONDS);
        set.add(3);
        
        assertThat(set.removeAll(Arrays.asList(1, 3))).isTrue();
        assertThat(set.removeAll(Arrays.asList(1, 3))).isFalse();
        assertThat(set).containsOnly(2);
        set.destroy();
    }
    
    @Test
    public void testDelete() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        assertThat(set.delete()).isFalse();
        set.add(1, 1, TimeUnit.SECONDS);
        assertThat(set.delete()).isTrue();
        assertThat(set.delete()).isFalse();
        set.destroy();
    }

    @Test
    public void testEmptyReadAll() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        assertThat(set.readAll()).isEmpty();
        set.destroy();
    }
    
    @Test
    public void testAddBigBean() {
        RSetCache<Map<Integer, Integer>> set = redisson.getSetCache("simple");
        Map<Integer, Integer> map = new HashMap<Integer, Integer>();
        for (int i = 0; i < 150; i++) {
            map.put(i, i);
        }
        set.add(map);
        map.remove(0);
        set.add(map);
        set.iterator().next();
        set.destroy();
    }
    
    @Test
    public void testAddBean() throws InterruptedException, ExecutionException {
        SimpleBean sb = new SimpleBean();
        sb.setLng(1L);
        RSetCache<SimpleBean> set = redisson.getSetCache("simple");
        assertThat(set.add(sb)).isTrue();
        Assertions.assertEquals(sb.getLng(), set.iterator().next().getLng());
        set.destroy();
    }

    @Test
    public void testAddExpire() throws InterruptedException, ExecutionException {
        RSetCache<String> set = redisson.getSetCache("simple3");
        assertThat(set.add("123", 500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(set).contains("123");

        Thread.sleep(500);

        assertThat(set.contains("123")).isFalse();
        
        assertThat(set.add("123", 1, TimeUnit.SECONDS)).isTrue();
        set.destroy();

    }

    @Test
    public void testAddOverrideExpiration() throws InterruptedException {
        RSetCache<String> set = redisson.getSetCache("simple31");
        assertThat(set.add("123", 500, TimeUnit.MILLISECONDS)).isTrue();
        Thread.sleep(400);
        assertThat(set.add("123", 3, TimeUnit.SECONDS)).isFalse();
        Thread.sleep(2000);
        assertThat(set.contains("123")).isTrue();
        set.destroy();
    }
    
    @Test
    public void testAddExpireTwise() throws InterruptedException, ExecutionException {
        RSetCache<String> set = redisson.getSetCache("simple31");
        assertThat(set.add("123", 1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(1000);

        Assertions.assertFalse(set.contains("123"));

        assertThat(set.add("4341", 1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(1000);

        Assertions.assertFalse(set.contains("4341"));
        set.destroy();
    }
    
    @Test
    public void testAddExpireThenAdd() throws InterruptedException, ExecutionException {
        RSetCache<String> set = redisson.getSetCache("simple31");
        assertThat(set.add("123", 500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(set.size()).isEqualTo(1);

        Thread.sleep(500);

        assertThat(set.size()).isZero();
        assertThat(set.contains("123")).isFalse();

        assertThat(set.add("123")).isTrue();
        Thread.sleep(1000);

        assertThat(set.contains("123")).isTrue();
        set.destroy();
    }


    @Test
    public void testExpireOverwrite() throws InterruptedException, ExecutionException {
        RSetCache<String> set = redisson.getSetCache("simple");
        assertThat(set.add("123", 1, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(800);

        assertThat(set.add("123", 1, TimeUnit.SECONDS)).isFalse();

        Thread.sleep(100);
        assertThat(set.contains("123")).isTrue();

        Thread.sleep(300);

        assertThat(set.contains("123")).isTrue();
        set.destroy();
    }

    @Test
    public void testRemove() throws InterruptedException, ExecutionException {
        RSetCache<Integer> set = redisson.getSetCache("simple");
        set.add(1, 1, TimeUnit.SECONDS);
        set.add(3, 2, TimeUnit.SECONDS);
        set.add(7, 3, TimeUnit.SECONDS);

        Assertions.assertTrue(set.remove(1));
        Assertions.assertFalse(set.contains(1));
        assertThat(set).containsOnly(3, 7);

        Assertions.assertFalse(set.remove(1));
        assertThat(set).containsOnly(3, 7);

        Assertions.assertTrue(set.remove(3));
        Assertions.assertFalse(set.contains(3));
        assertThat(set).containsOnly(7);
        Assertions.assertEquals(1, set.size());
        set.destroy();
    }

    @Test
    public void testIteratorRemove() throws InterruptedException {
        RSetCache<String> set = redisson.getSetCache("list");
        set.add("1");
        set.add("4", 1, TimeUnit.SECONDS);
        set.add("2");
        set.add("5", 1, TimeUnit.SECONDS);
        set.add("3");

        Thread.sleep(1000);

        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            String value = iterator.next();
            if (value.equals("2")) {
                iterator.remove();
            }
        }

        assertThat(set).contains("1", "3");

        int iteration = 0;
        for (Iterator<String> iterator = set.iterator(); iterator.hasNext();) {
            iterator.next();
            iterator.remove();
            iteration++;
        }

        Assertions.assertEquals(2, iteration);

        Assertions.assertFalse(set.contains("4"));
        Assertions.assertFalse(set.contains("5"));
        set.destroy();
    }

    @Test
    public void testIteratorSequence() {
        RSetCache<Long> set = redisson.getSetCache("set");
        for (int i = 0; i < 1000; i++) {
            set.add(Long.valueOf(i));
        }

        Set<Long> setCopy = new HashSet<Long>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Long.valueOf(i));
        }

        checkIterator(set, setCopy);
        set.destroy();
    }

    private void checkIterator(Set<Long> set, Set<Long> setCopy) {
        for (Iterator<Long> iterator = set.iterator(); iterator.hasNext();) {
            Long value = iterator.next();
            if (!setCopy.remove(value)) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, setCopy.size());
    }

    @Test
    public void testRetainAll() throws InterruptedException {
        RSetCache<Integer> set = redisson.getSetCache("set");
        for (int i = 0; i < 10000; i++) {
            set.add(i);
            set.add(i*10, 15, TimeUnit.SECONDS);
        }

        Assertions.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Thread.sleep(500);
        assertThat(set).containsOnly(1, 2);
        Assertions.assertEquals(2, set.size());
        set.destroy();
    }

    @Test
    public void testIteratorRemoveHighVolume() throws InterruptedException {
        RSetCache<Integer> set = redisson.getSetCache("set");
        for (int i = 1; i <= 5000; i++) {
            set.add(i);
            set.add(i*100000, 20, TimeUnit.SECONDS);
        }
        int cnt = 0;

        Iterator<Integer> iterator = set.iterator();
        while (iterator.hasNext()) {
            Integer integer = iterator.next();
            iterator.remove();
            cnt++;
        }
        Assertions.assertEquals(10000, cnt);
        Assertions.assertEquals(0, set.size());
        set.destroy();
    }

    @Test
    public void testContainsAll() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assertions.assertTrue(set.containsAll(Collections.emptyList()));
        Assertions.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assertions.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
        set.destroy();
    }

    @Test
    public void testToArray() throws InterruptedException {
        RSetCache<String> set = redisson.getSetCache("set");
        set.add("1");
        set.add("4");
        set.add("2", 1, TimeUnit.SECONDS);
        set.add("5");
        set.add("3");

        Thread.sleep(1500);

        assertThat(set.toArray()).containsOnly("1", "4", "5", "3");

        String[] strs = set.toArray(new String[0]);
        assertThat(strs).containsOnly("1", "4", "5", "3");
        set.destroy();
    }

    @Test
    public void testContains() throws InterruptedException {
        RSetCache<TestObject> set = redisson.getSetCache("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"), 1, TimeUnit.SECONDS);
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Thread.sleep(1000);

        Assertions.assertFalse(set.contains(new TestObject("2", "3")));
        Assertions.assertTrue(set.contains(new TestObject("1", "2")));
        Assertions.assertFalse(set.contains(new TestObject("1", "9")));
        set.destroy();
    }

    @Test
    public void testDuplicates() {
        RSetCache<TestObject> set = redisson.getSetCache("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assertions.assertEquals(4, set.size());
        set.destroy();
    }

    @Test
    public void testSize() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(5);

        Assertions.assertEquals(5, set.size());
        set.destroy();
    }

    @Test
    public void testReadAllExpired() throws InterruptedException {
        RSetCache<Integer> set = redisson.getSetCache("set");
        set.add(1, 1, TimeUnit.SECONDS);
        Thread.sleep(1005);
        assertThat(set.readAll()).isEmpty();
        set.destroy();
    }
    
    @Test
    public void testReadAll() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        set.add(1, 2, TimeUnit.MINUTES);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        assertThat(set.readAll()).containsOnly(1, 2, 3, 4, 5);
        set.destroy();
    }

    @Test
    public void testRetainAllEmpty() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        Assertions.assertTrue(set.retainAll(Collections.<Integer>emptyList()));
        Assertions.assertEquals(0, set.size());
        set.destroy();
    }

    @Test
    public void testRetainAllNoModify() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        set.add(1);
        set.add(2);

        Assertions.assertFalse(set.retainAll(Arrays.asList(1, 2))); // nothing changed
        assertThat(set).containsOnly(1, 2);
        set.destroy();
    }

    @Test
    public void testExpiredIterator() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("simple");
        cache.add("0");
        cache.add("1", 1, TimeUnit.SECONDS);
        cache.add("2", 3, TimeUnit.SECONDS);
        cache.add("3", 4, TimeUnit.SECONDS);
        cache.add("4", 1, TimeUnit.SECONDS);

        Thread.sleep(1000);

        assertThat(cache).contains("0", "2", "3");
        cache.destroy();
    }

    @Test
    public void testExpire() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("simple");
        cache.add("8", 1, TimeUnit.SECONDS);

        cache.expire(Duration.ofMillis(100));

        Thread.sleep(500);

        Assertions.assertEquals(0, cache.size());
        cache.destroy();
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("simple");
        cache.add("8", 1, TimeUnit.SECONDS);

        cache.expireAt(System.currentTimeMillis() + 100);

        Thread.sleep(500);

        Assertions.assertEquals(0, cache.size());
        cache.destroy();
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("simple");
        cache.add("8", 1, TimeUnit.SECONDS);

        cache.expireAt(System.currentTimeMillis() + 100);

        cache.clearExpire();

        Thread.sleep(500);

        Assertions.assertEquals(1, cache.size());
        cache.destroy();
    }

    @Test
    public void testScheduler() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("simple33");
        Assertions.assertFalse(cache.contains("33"));

        Assertions.assertTrue(cache.add("33", 5, TimeUnit.SECONDS));

        Thread.sleep(11000);

        Assertions.assertEquals(0, cache.size());
        cache.destroy();
    }

    @Test
    public void testUnion() throws InterruptedException {
        redisson.getKeys().flushall();
        RSetCache<Integer> cache1 = redisson.getSetCache("cache1", IntegerCodec.INSTANCE);
        cache1.add(1);
        cache1.add(2, 1, TimeUnit.SECONDS);
        cache1.add(5, 1, TimeUnit.SECONDS);
        cache1.add(3);

        RSetCache<Integer> cache2 = redisson.getSetCache("cache2", IntegerCodec.INSTANCE);
        cache2.add(4);
        cache2.add(2, 1, TimeUnit.SECONDS);
        cache2.add(5, 1, TimeUnit.SECONDS);
        cache2.add(7);

        Set<Integer> ff = cache1.readUnion("cache2");
        assertThat(ff).containsOnly(1, 2, 5, 3, 4, 7);


        RSetCache<Integer> cache3 = redisson.getSetCache("cache3", IntegerCodec.INSTANCE);
        assertThat(cache3.union("cache1", "cache2")).isEqualTo(6);
        assertThat(cache3).containsExactlyInAnyOrder(1, 3, 2, 5, 4, 7);
        cache3.clear();

        Thread.sleep(1500);

        assertThat(cache3.union("cache1", "cache2")).isEqualTo(4);
        assertThat(cache3).containsExactlyInAnyOrder(1, 3, 4, 7);

        assertThat(redisson.getKeys().getKeys()).containsExactlyInAnyOrder("cache1", "cache2", "cache3");
    }

    @Test
    public void testDiff() throws InterruptedException {
        redisson.getKeys().flushall();
        RSetCache<Integer> cache1 = redisson.getSetCache("cache1", IntegerCodec.INSTANCE);
        cache1.add(1);
        cache1.add(2, 1, TimeUnit.SECONDS);
        cache1.add(5, 1, TimeUnit.SECONDS);
        cache1.add(3, 1, TimeUnit.SECONDS);

        RSetCache<Integer> cache2 = redisson.getSetCache("cache2", IntegerCodec.INSTANCE);
        cache2.add(4);
        cache2.add(2, 1, TimeUnit.SECONDS);
        cache2.add(5, 1, TimeUnit.SECONDS);
        cache2.add(7);

        Set<Integer> ff = cache1.readDiff("cache2");
        assertThat(ff).containsOnly(3, 1);

        RSetCache<Integer> cache3 = redisson.getSetCache("cache3", IntegerCodec.INSTANCE);
        assertThat(cache3.diff("cache1", "cache2")).isEqualTo(2);
        assertThat(cache3).containsExactlyInAnyOrder(1, 3);
        cache3.clear();

        Thread.sleep(1500);

        assertThat(cache3.diff("cache1", "cache2")).isEqualTo(1);
        assertThat(cache3).containsExactlyInAnyOrder(1);

        assertThat(redisson.getKeys().getKeys()).containsExactlyInAnyOrder("cache1", "cache2", "cache3");
    }

        @Test
    public void testIntersection() throws InterruptedException {
        redisson.getKeys().flushall();
        RSetCache<Integer> cache1 = redisson.getSetCache("cache1");
        cache1.add(1);
        cache1.add(2, 1, TimeUnit.SECONDS);
        cache1.add(5, 1, TimeUnit.SECONDS);
        cache1.add(3);

        RSetCache<Integer> cache2 = redisson.getSetCache("cache2");
        cache2.add(4);
        cache2.add(2, 1, TimeUnit.SECONDS);
        cache2.add(5, 1, TimeUnit.SECONDS);
        cache2.add(7);

        Set<Integer> ff = cache1.readIntersection("cache2");
        assertThat(ff).containsOnly(2, 5);


        assertThat(cache1.countIntersection("cache2")).isEqualTo(2);
        RSetCache<Integer> cache3 = redisson.getSetCache("cache3");
        assertThat(cache3.intersection("cache1", "cache2")).isEqualTo(2);
        assertThat(cache3).containsExactlyInAnyOrder(2, 5);
        cache3.clear();

        Thread.sleep(1500);

        assertThat(cache1.countIntersection("cache2")).isEqualTo(0);
        assertThat(cache3.intersection("cache1", "cache2")).isEqualTo(0);
        assertThat(cache3).isEmpty();

        assertThat(redisson.getKeys().count()).isEqualTo(2);
        assertThat(redisson.getKeys().getKeys()).containsExactlyInAnyOrder("cache1", "cache2");
    }

    @Test
    public void testAddListener() {
        testWithParams(redisson -> {
            RSetCache<Integer> ss = redisson.getSetCache("test");
            AtomicInteger latch = new AtomicInteger();
            int id = ss.addListener(new SetAddListener() {
                @Override
                public void onAdd(String name) {
                    latch.incrementAndGet();
                }
            });
            ss.add(1, 10, TimeUnit.SECONDS);

            Awaitility.await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
                assertThat(latch.get()).isEqualTo(1);
            });

            ss.destroy();

            ss.add(1, 10, TimeUnit.SECONDS);

            Awaitility.await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(latch.get()).isEqualTo(1));
        }, NOTIFY_KEYSPACE_EVENTS, "Ez");
    }

    @Test
    public void testAddIfAbsentWithMapParam() throws InterruptedException {
        redisson.getKeys().flushall();
        RSetCache<String> cache = redisson.getSetCache("cache");
        Map<String, Duration> map = new HashMap<>();
        map.put("key1", Duration.ofMinutes(1));
        map.put("key2", Duration.ofMinutes(1));
        assertThat(cache.addIfAbsent(map)).isTrue();
        map = new HashMap<>();
        map.put("key1", Duration.ofMinutes(1));
        assertThat(cache.addIfAbsent(map)).isFalse();
        map = new HashMap<>();
        map.put("key3", Duration.ofSeconds(1));
        assertThat(cache.addIfAbsent(map)).isTrue();
        Thread.sleep(1200);
        assertThat(cache.addIfAbsent(map)).isTrue();
        redisson.getKeys().flushall();
    }
}
