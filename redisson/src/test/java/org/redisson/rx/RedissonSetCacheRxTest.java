package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.TestObject;
import org.redisson.api.RSetCacheRx;

public class RedissonSetCacheRxTest extends BaseRxTest {

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
    public void testAddBean() throws InterruptedException, ExecutionException {
        SimpleBean sb = new SimpleBean();
        sb.setLng(1L);
        RSetCacheRx<SimpleBean> set = redisson.getSetCache("simple");
        sync(set.add(sb));
        Assert.assertEquals(sb.getLng(), toIterator(set.iterator()).next().getLng());
    }

    @Test
    public void testAddExpire() throws InterruptedException, ExecutionException {
        RSetCacheRx<String> set = redisson.getSetCache("simple3");
        sync(set.add("123", 1, TimeUnit.SECONDS));
        assertThat(sync(set)).containsOnly("123");

        Thread.sleep(1000);

        Assert.assertFalse(sync(set.contains("123")));
    }

    @Test
    public void testAddExpireTwise() throws InterruptedException, ExecutionException {
        RSetCacheRx<String> set = redisson.getSetCache("simple31");
        sync(set.add("123", 1, TimeUnit.SECONDS));
        Thread.sleep(1000);

        Assert.assertFalse(sync(set.contains("123")));

        sync(set.add("4341", 1, TimeUnit.SECONDS));
        Thread.sleep(1000);

        Assert.assertFalse(sync(set.contains("4341")));
    }

    @Test
    public void testExpireOverwrite() throws InterruptedException, ExecutionException {
        RSetCacheRx<String> set = redisson.getSetCache("simple");
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
        RSetCacheRx<Integer> set = redisson.getSetCache("simple");
        sync(set.add(1, 1, TimeUnit.SECONDS));
        sync(set.add(3, 2, TimeUnit.SECONDS));
        sync(set.add(7, 3, TimeUnit.SECONDS));

        Assert.assertTrue(sync(set.remove(1)));
        Assert.assertFalse(sync(set.contains(1)));
        assertThat(sync(set)).contains(3, 7);

        Assert.assertFalse(sync(set.remove(1)));
        assertThat(sync(set)).contains(3, 7);

        Assert.assertTrue(sync(set.remove(3)));
        Assert.assertFalse(sync(set.contains(3)));
        assertThat(sync(set)).contains(7);
        Assert.assertEquals(1, sync(set.size()).intValue());
    }

    @Test
    public void testIteratorSequence() throws InterruptedException {
        RSetCacheRx<Long> set = redisson.getSetCache("set");
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

    private void checkIterator(RSetCacheRx<Long> set, Set<Long> setCopy) {
        for (Iterator<Long> iterator = toIterator(set.iterator()); iterator.hasNext();) {
            Long value = iterator.next();
            if (!setCopy.remove(value)) {
                Assert.fail();
            }
        }

        Assert.assertEquals(0, setCopy.size());
    }

    @Test
    public void testRetainAll() {
        RSetCacheRx<Integer> set = redisson.getSetCache("set");
        for (int i = 0; i < 10000; i++) {
            sync(set.add(i));
            sync(set.add(i*10, 10, TimeUnit.SECONDS));
        }

        Assert.assertTrue(sync(set.retainAll(Arrays.asList(1, 2))));
        assertThat(sync(set)).contains(1, 2);
        Assert.assertEquals(2, sync(set.size()).intValue());
    }

    @Test
    public void testContainsAll() {
        RSetCacheRx<Integer> set = redisson.getSetCache("set");
        for (int i = 0; i < 200; i++) {
            sync(set.add(i));
        }

        Assert.assertTrue(sync(set.containsAll(Collections.emptyList())));
        Assert.assertTrue(sync(set.containsAll(Arrays.asList(30, 11))));
        Assert.assertFalse(sync(set.containsAll(Arrays.asList(30, 711, 11))));
    }

    @Test
    public void testContains() throws InterruptedException {
        RSetCacheRx<TestObject> set = redisson.getSetCache("set");

        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("2", "3"), 1, TimeUnit.SECONDS));
        sync(set.add(new TestObject("3", "4")));
        sync(set.add(new TestObject("5", "6")));

        Thread.sleep(1000);

        Assert.assertFalse(sync(set.contains(new TestObject("2", "3"))));
        Assert.assertTrue(sync(set.contains(new TestObject("1", "2"))));
        Assert.assertFalse(sync(set.contains(new TestObject("1", "9"))));
    }

    @Test
    public void testDuplicates() {
        RSetCacheRx<TestObject> set = redisson.getSetCache("set");

        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("2", "3")));
        sync(set.add(new TestObject("3", "4")));
        sync(set.add(new TestObject("5", "6")));

        Assert.assertEquals(4, sync(set.size()).intValue());
    }

    @Test
    public void testSize() {
        RSetCacheRx<Integer> set = redisson.getSetCache("set");
        Assert.assertEquals(true, sync(set.add(1)));
        Assert.assertEquals(true, sync(set.add(2)));
        Assert.assertEquals(true, sync(set.add(3)));
        Assert.assertEquals(false, sync(set.add(3)));
        Assert.assertEquals(false, sync(set.add(3)));
        Assert.assertEquals(true, sync(set.add(4)));
        Assert.assertEquals(true, sync(set.add(5)));
        Assert.assertEquals(false, sync(set.add(5)));

        Assert.assertEquals(5, sync(set.size()).intValue());
    }


    @Test
    public void testRetainAllEmpty() {
        RSetCacheRx<Integer> set = redisson.getSetCache("set");
        sync(set.add(1));
        sync(set.add(2));
        sync(set.add(3));
        sync(set.add(4));
        sync(set.add(5));

        Assert.assertTrue(sync(set.retainAll(Collections.<Integer>emptyList())));
        Assert.assertEquals(0, sync(set.size()).intValue());
    }

    @Test
    public void testRetainAllNoModify() {
        RSetCacheRx<Integer> set = redisson.getSetCache("set");
        sync(set.add(1));
        sync(set.add(2));

        Assert.assertFalse(sync(set.retainAll(Arrays.asList(1, 2)))); // nothing changed
        assertThat(sync(set)).contains(1, 2);
    }

    @Test
    public void testExpiredIterator() throws InterruptedException {
        RSetCacheRx<String> cache = redisson.getSetCache("simple");
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
        RSetCacheRx<String> cache = redisson.getSetCache("simple");
        sync(cache.add("8", 1, TimeUnit.SECONDS));

        sync(cache.expire(100, TimeUnit.MILLISECONDS));

        Thread.sleep(500);

        Assert.assertEquals(0, sync(cache.size()).intValue());
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RSetCacheRx<String> cache = redisson.getSetCache("simple");
        sync(cache.add("8", 1, TimeUnit.SECONDS));

        sync(cache.expireAt(System.currentTimeMillis() + 100));

        Thread.sleep(500);

        Assert.assertEquals(0, sync(cache.size()).intValue());
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RSetCacheRx<String> cache = redisson.getSetCache("simple");
        sync(cache.add("8", 1, TimeUnit.SECONDS));

        sync(cache.expireAt(System.currentTimeMillis() + 100));

        sync(cache.clearExpire());

        Thread.sleep(500);

        Assert.assertEquals(1, sync(cache.size()).intValue());
    }

    @Test
    public void testScheduler() throws InterruptedException {
        RSetCacheRx<String> cache = redisson.getSetCache("simple33");
        Assert.assertFalse(sync(cache.contains("33")));

        Assert.assertTrue(sync(cache.add("33", 5, TimeUnit.SECONDS)));

        Thread.sleep(11000);

        Assert.assertEquals(0, sync(cache.size()).intValue());

    }

}
