package org.redisson;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSetReactive;

public class RedissonSetReactiveTest extends BaseReactiveTest {

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
    public void testAddAllReactive() {
        RSetReactive<Integer> list = redisson.getSet("set");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        RSetReactive<Integer> list2 = redisson.getSet("set2");
        Assertions.assertEquals(true, sync(list2.addAll(list.iterator())));
        Assertions.assertEquals(5, sync(list2.size()).intValue());
    }

    @Test
    public void testRemoveRandom() {
        RSetReactive<Integer> set = redisson.getSet("simple");
        sync(set.add(1));
        sync(set.add(2));
        sync(set.add(3));

        assertThat(sync(set.removeRandom())).isIn(1, 2, 3);
        assertThat(sync(set.removeRandom())).isIn(1, 2, 3);
        assertThat(sync(set.removeRandom())).isIn(1, 2, 3);
        Assertions.assertNull(sync(set.removeRandom()));
    }

    @Test
    public void testRandom() {
        RSetReactive<Integer> set = redisson.getSet("simple");
        sync(set.add(1));
        sync(set.add(2));
        sync(set.add(3));

        assertThat(sync(set.random())).isIn(1, 2, 3);
        assertThat(sync(set.random())).isIn(1, 2, 3);
        assertThat(sync(set.random())).isIn(1, 2, 3);

        assertThat(sync(set)).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    public void testAddBean() throws InterruptedException, ExecutionException {
        SimpleBean sb = new SimpleBean();
        sb.setLng(1L);
        RSetReactive<SimpleBean> set = redisson.getSet("simple");
        sync(set.add(sb));
        Assertions.assertEquals(sb.getLng(), toIterator(set.iterator()).next().getLng());
    }

    @Test
    public void testAddLong() throws InterruptedException, ExecutionException {
        Long sb = 1l;

        RSetReactive<Long> set = redisson.getSet("simple_longs");
        sync(set.add(sb));

        for (Long l : sync(set)) {
            Assertions.assertEquals(sb.getClass(), l.getClass());
        }
    }

    @Test
    public void testRemove() throws InterruptedException, ExecutionException {
        RSetReactive<Integer> set = redisson.getSet("simple");
        sync(set.add(1));
        sync(set.add(3));
        sync(set.add(7));

        Assertions.assertTrue(sync(set.remove(1)));
        Assertions.assertFalse(sync(set.contains(1)));
        assertThat(sync(set)).containsExactlyInAnyOrder(3, 7);

        Assertions.assertFalse(sync(set.remove(1)));
        assertThat(sync(set)).containsExactlyInAnyOrder(3, 7);

        sync(set.remove(3));
        Assertions.assertFalse(sync(set.contains(3)));
        assertThat(sync(set)).containsExactlyInAnyOrder(7);
    }

    @Test
    public void testIteratorSequence() {
        RSetReactive<Long> set = redisson.getSet("set");
        for (int i = 0; i < 1000; i++) {
            sync(set.add(Long.valueOf(i)));
        }

        Set<Long> setCopy = new HashSet<Long>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Long.valueOf(i));
        }

        checkIterator(set, setCopy);
    }

    private void checkIterator(RSetReactive<Long> set, Set<Long> setCopy) {
        for (Iterator<Long> iterator = toIterator(set.iterator()); iterator.hasNext(); ) {
            Long value = iterator.next();
            if (!setCopy.remove(value)) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, setCopy.size());
    }

    @Test
    public void testLong() {
        RSetReactive<Long> set = redisson.getSet("set");
        sync(set.add(1L));
        sync(set.add(2L));

        assertThat(sync(set)).containsOnly(1L, 2L);
    }

    @Test
    public void testRetainAll() {
        RSetReactive<Integer> set = redisson.getSet("set");
        for (int i = 0; i < 20000; i++) {
            sync(set.add(i));
        }

        Assertions.assertTrue(sync(set.retainAll(Arrays.asList(1, 2))));
        assertThat(sync(set)).containsExactlyInAnyOrder(1, 2);
        Assertions.assertEquals(2, sync(set.size()).intValue());
    }

    @Test
    public void testContainsAll() {
        RSetReactive<Integer> set = redisson.getSet("set");
        for (int i = 0; i < 200; i++) {
            sync(set.add(i));
        }

        Assertions.assertTrue(sync(set.containsAll(Collections.emptyList())));
        Assertions.assertTrue(sync(set.containsAll(Arrays.asList(30, 11))));
        Assertions.assertFalse(sync(set.containsAll(Arrays.asList(30, 711, 11))));
    }

    @Test
    public void testContains() {
        RSetReactive<TestObject> set = redisson.getSet("set");

        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("2", "3")));
        sync(set.add(new TestObject("3", "4")));
        sync(set.add(new TestObject("5", "6")));

        Assertions.assertTrue(sync(set.contains(new TestObject("2", "3"))));
        Assertions.assertTrue(sync(set.contains(new TestObject("1", "2"))));
        Assertions.assertFalse(sync(set.contains(new TestObject("1", "9"))));
    }

    @Test
    public void testDuplicates() {
        RSetReactive<TestObject> set = redisson.getSet("set");

        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("1", "2")));
        sync(set.add(new TestObject("2", "3")));
        sync(set.add(new TestObject("3", "4")));
        sync(set.add(new TestObject("5", "6")));

        Assertions.assertEquals(4, sync(set.size()).intValue());
    }

    @Test
    public void testSize() {
        RSetReactive<Integer> set = redisson.getSet("set");
        sync(set.add(1));
        sync(set.add(2));
        sync(set.add(3));
        sync(set.add(3));
        sync(set.add(4));
        sync(set.add(5));
        sync(set.add(5));

        Assertions.assertEquals(5, sync(set.size()).intValue());
    }


    @Test
    public void testRetainAllEmpty() {
        RSetReactive<Integer> set = redisson.getSet("set");
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
        RSetReactive<Integer> set = redisson.getSet("set");
        sync(set.add(1));
        sync(set.add(2));

        Assertions.assertFalse(sync(set.retainAll(Arrays.asList(1, 2)))); // nothing changed
        assertThat(sync(set)).containsExactly(1, 2);
    }


    @Test
    public void testMove() throws Exception {
        RSetReactive<Integer> set = redisson.getSet("set");
        RSetReactive<Integer> otherSet = redisson.getSet("otherSet");

        sync(set.add(1));
        sync(set.add(2));

        Assertions.assertTrue(sync(set.move("otherSet", 1)));

        Assertions.assertEquals(1, sync(set.size()).intValue());
        assertThat(sync(set)).containsExactly(2);

        Assertions.assertEquals(1, sync(otherSet.size()).intValue());
        assertThat(sync(otherSet)).containsExactly(1);
    }

    @Test
    public void testMoveNoMember() throws Exception {
        RSetReactive<Integer> set = redisson.getSet("set");
        RSetReactive<Integer> otherSet = redisson.getSet("otherSet");

        sync(set.add(1));

        Assertions.assertFalse(sync(set.move("otherSet", 2)));

        Assertions.assertEquals(1, sync(set.size()).intValue());
        Assertions.assertEquals(0, sync(otherSet.size()).intValue());
    }

    @Test
    public void testIntersection() {
        final String firstSetName = "firstSet";
        RSetReactive<Integer> firstSet = redisson.getSet(firstSetName);

        sync(firstSet.add(1));
        sync(firstSet.add(2));
        sync(firstSet.add(3));

        final String secondSetName = "secondSet";
        RSetReactive<Integer> secondSet = redisson.getSet(secondSetName);

        sync(secondSet.add(3));
        sync(secondSet.add(4));
        sync(secondSet.add(1));

        final RSetReactive<Object> tmp = redisson.getSet("tmp");

        final Integer count = sync(tmp.intersection(firstSetName, secondSetName));

        Assertions.assertEquals(2, count.intValue());

        Assertions.assertTrue(sync(tmp.contains(1)));
        Assertions.assertTrue(sync(tmp.contains(3)));
    }
}
