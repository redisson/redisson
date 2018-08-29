package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RSetCache;
import org.redisson.eviction.EvictionScheduler;

import mockit.Deencapsulation;

public class RedissonSetCacheTest extends BaseTest {

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
    public void testDestroy() {
        RSetCache<String> cache = redisson.getSetCache("test");
        
        EvictionScheduler evictionScheduler = ((Redisson)redisson).getEvictionScheduler();
        Map<?, ?> map = Deencapsulation.getField(evictionScheduler, "tasks");
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
        Assert.assertEquals(sb.getLng(), set.iterator().next().getLng());
        set.destroy();
    }

    @Test
    public void testAddExpire() throws InterruptedException, ExecutionException {
        RSetCache<String> set = redisson.getSetCache("simple3");
        assertThat(set.add("123", 500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(set).contains("123");

        Thread.sleep(500);
        
        assertThat(set.size()).isEqualTo(1);
        assertThat(set).doesNotContain("123");
        
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

        Assert.assertFalse(set.contains("123"));

        assertThat(set.add("4341", 1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(1000);

        Assert.assertFalse(set.contains("4341"));
        set.destroy();
    }
    
    @Test
    public void testAddExpireThenAdd() throws InterruptedException, ExecutionException {
        RSetCache<String> set = redisson.getSetCache("simple31");
        assertThat(set.add("123", 500, TimeUnit.MILLISECONDS)).isTrue();
        
        Thread.sleep(500);

        assertThat(set.size()).isEqualTo(1);
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

        Assert.assertTrue(set.remove(1));
        Assert.assertFalse(set.contains(1));
        assertThat(set).containsOnly(3, 7);

        Assert.assertFalse(set.remove(1));
        assertThat(set).containsOnly(3, 7);

        Assert.assertTrue(set.remove(3));
        Assert.assertFalse(set.contains(3));
        assertThat(set).containsOnly(7);
        Assert.assertEquals(1, set.size());
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

        Assert.assertEquals(2, iteration);

        Assert.assertFalse(set.contains("4"));
        Assert.assertFalse(set.contains("5"));
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
                Assert.fail();
            }
        }

        Assert.assertEquals(0, setCopy.size());
    }

    @Test
    public void testRetainAll() throws InterruptedException {
        RSetCache<Integer> set = redisson.getSetCache("set");
        for (int i = 0; i < 10000; i++) {
            set.add(i);
            set.add(i*10, 15, TimeUnit.SECONDS);
        }

        Assert.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Thread.sleep(500);
        assertThat(set).containsOnly(1, 2);
        Assert.assertEquals(2, set.size());
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
        Assert.assertEquals(10000, cnt);
        Assert.assertEquals(0, set.size());
        set.destroy();
    }

    @Test
    public void testContainsAll() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.containsAll(Collections.emptyList()));
        Assert.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
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

        Assert.assertFalse(set.contains(new TestObject("2", "3")));
        Assert.assertTrue(set.contains(new TestObject("1", "2")));
        Assert.assertFalse(set.contains(new TestObject("1", "9")));
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

        Assert.assertEquals(4, set.size());
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

        Assert.assertEquals(5, set.size());
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

        Assert.assertTrue(set.retainAll(Collections.<Integer>emptyList()));
        Assert.assertEquals(0, set.size());
        set.destroy();
    }

    @Test
    public void testRetainAllNoModify() {
        RSetCache<Integer> set = redisson.getSetCache("set");
        set.add(1);
        set.add(2);

        Assert.assertFalse(set.retainAll(Arrays.asList(1, 2))); // nothing changed
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

        cache.expire(100, TimeUnit.MILLISECONDS);

        Thread.sleep(500);

        Assert.assertEquals(0, cache.size());
        cache.destroy();
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("simple");
        cache.add("8", 1, TimeUnit.SECONDS);

        cache.expireAt(System.currentTimeMillis() + 100);

        Thread.sleep(500);

        Assert.assertEquals(0, cache.size());
        cache.destroy();
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("simple");
        cache.add("8", 1, TimeUnit.SECONDS);

        cache.expireAt(System.currentTimeMillis() + 100);

        cache.clearExpire();

        Thread.sleep(500);

        Assert.assertEquals(1, cache.size());
        cache.destroy();
    }

    @Test
    public void testScheduler() throws InterruptedException {
        RSetCache<String> cache = redisson.getSetCache("simple33");
        Assert.assertFalse(cache.contains("33"));

        Assert.assertTrue(cache.add("33", 5, TimeUnit.SECONDS));

        Thread.sleep(11000);

        Assert.assertEquals(0, cache.size());
        cache.destroy();

    }

}
