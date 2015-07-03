package org.redisson;

import io.netty.util.concurrent.Future;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ExecutionException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RSet;
import org.redisson.core.RSortedSet;

public class RedissonSetTest extends BaseTest {

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
        RSet<SimpleBean> set = redisson.getSet("simple");
        set.add(sb);
        Assert.assertEquals(sb.getLng(), set.iterator().next().getLng());
    }

    @Test
    public void testAddLong() throws InterruptedException, ExecutionException {
        Long sb = 1l;

        RSet<Long> set = redisson.getSet("simple_longs");
        set.add(sb);

        for (Long l : set) {
            Assert.assertEquals(sb.getClass(), l.getClass());
        }

        Object[] arr = set.toArray();

        for (Object o : arr) {
            Assert.assertEquals(sb.getClass(), o.getClass());
        }
    }

    @Test
    public void testAddAsync() throws InterruptedException, ExecutionException {
        RSet<Integer> set = redisson.getSet("simple");
        Future<Boolean> future = set.addAsync(2);
        Assert.assertTrue(future.get());

        Assert.assertTrue(set.contains(2));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RSet<Integer> set = redisson.getSet("simple");
        set.add(1);
        set.add(3);
        set.add(7);

        Assert.assertTrue(set.removeAsync(1).get());
        Assert.assertFalse(set.contains(1));
        Assert.assertThat(set, Matchers.containsInAnyOrder(3, 7));

        Assert.assertFalse(set.removeAsync(1).get());
        Assert.assertThat(set, Matchers.containsInAnyOrder(3, 7));

        set.removeAsync(3).get();
        Assert.assertFalse(set.contains(3));
        Assert.assertThat(set, Matchers.contains(7));
    }

    @Test
    public void testIteratorRemove() {
        Set<String> list = redisson.getSet("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            String value = iterator.next();
            if (value.equals("2")) {
                iterator.remove();
            }
        }

        Assert.assertThat(list, Matchers.containsInAnyOrder("1", "4", "5", "3"));

        int iteration = 0;
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            iterator.next();
            iterator.remove();
            iteration++;
        }

        Assert.assertEquals(4, iteration);

        Assert.assertEquals(0, list.size());
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void testIteratorSequence() {
        Set<Long> set = redisson.getSet("set");
        for (int i = 0; i < 1000; i++) {
            set.add(Long.valueOf(i));
        }

        Set<Long> setCopy = new HashSet<Long>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Long.valueOf(i));
        }

        checkIterator(set, setCopy);
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
    public void testLong() {
        Set<Long> set = redisson.getSet("set");
        set.add(1L);
        set.add(2L);

        Assert.assertThat(set, Matchers.containsInAnyOrder(1L, 2L));
    }

    @Test
    public void testRetainAll() {
        Set<Integer> set = redisson.getSet("set");
        for (int i = 0; i < 20000; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Assert.assertThat(set, Matchers.containsInAnyOrder(1, 2));
        Assert.assertEquals(2, set.size());
    }

//    @Test
//    public void testIteratorRemoveHighVolume() {
//        Set<Integer> set = redisson.getSet("set") /*new HashSet<Integer>()*/;
//        for (int i = 0; i < 120000; i++) {
//            set.add(i);
//        }
//        int cnt = 0;
//        Iterator<Integer> iterator = set.iterator();
//        while (iterator.hasNext()) {
//            Integer integer = iterator.next();
//            if (integer > -1) { // always
//                iterator.remove();
//            }
//            cnt++;
//        }
//        System.out.println("-----------");
//        for (Integer integer : set) {
//            System.out.println(integer);
//        }
//        Assert.assertEquals(20000, cnt);
//    }

    @Test
    public void testContainsAll() {
        Set<Integer> set = redisson.getSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
    }

    @Test
    public void testToArray() {
        Set<String> set = redisson.getSet("set");
        set.add("1");
        set.add("4");
        set.add("2");
        set.add("5");
        set.add("3");

        MatcherAssert.assertThat(Arrays.asList(set.toArray()), Matchers.<Object>containsInAnyOrder("1", "2", "4", "5", "3"));

        String[] strs = set.toArray(new String[0]);
        MatcherAssert.assertThat(Arrays.asList(strs), Matchers.containsInAnyOrder("1", "4", "2", "5", "3"));
    }

    @Test
    public void testContains() {
        Set<TestObject> set = redisson.getSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assert.assertTrue(set.contains(new TestObject("2", "3")));
        Assert.assertTrue(set.contains(new TestObject("1", "2")));
        Assert.assertFalse(set.contains(new TestObject("1", "9")));
    }

    @Test
    public void testDuplicates() {
        Set<TestObject> set = redisson.getSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assert.assertEquals(4, set.size());
    }

    @Test
    public void testSize() {
        Set<Integer> set = redisson.getSet("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(5);

        Assert.assertEquals(5, set.size());
    }


    @Test
    public void testRetainAllEmpty() {
        Set<Integer> set = redisson.getSet("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        Assert.assertTrue(set.retainAll(Collections.<Integer>emptyList()));
        Assert.assertEquals(0, set.size());
    }

    @Test
    public void testRetainAllNoModify() {
        Set<Integer> set = redisson.getSet("set");
        set.add(1);
        set.add(2);

        Assert.assertFalse(set.retainAll(Arrays.asList(1, 2))); // nothing changed
        Assert.assertThat(set, Matchers.containsInAnyOrder(1, 2));
    }
}
