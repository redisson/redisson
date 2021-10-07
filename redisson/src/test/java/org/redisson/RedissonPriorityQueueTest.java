package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RPriorityQueue;

public class RedissonPriorityQueueTest extends BaseTest {

    public static class Entry implements Comparable<Entry>, Serializable {

        private String key;
        private Integer value;

        public Entry(String key, Integer value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public int compareTo(Entry o) {
            return key.compareTo(o.key);
        }

    }

    @Test
    public void testComparable() {
        RPriorityQueue<Entry> queue = redisson.getPriorityQueue("anyQueue");
        queue.add(new Entry("b", 1));
        queue.add(new Entry("c", 1));
        queue.add(new Entry("a", 1));

        // Entry [a:1]
        Entry e = queue.poll();
        assertThat(e.key).isEqualTo("a");
        Entry e1 = queue.poll();
        assertThat(e1.key).isEqualTo("b");
    }

    @Test
    public void testPollLastAndOfferFirstTo() {
        RPriorityQueue<Integer> queue1 = redisson.getPriorityQueue("deque1");
        queue1.add(2);
        queue1.add(1);
        queue1.add(3);

        RPriorityQueue<Integer> queue2 = redisson.getPriorityQueue("deque2");
        queue2.add(5);
        queue2.add(4);
        queue2.add(6);
        
        queue1.pollLastAndOfferFirstTo(queue2.getName());
        assertThat(queue2).containsExactly(3, 4, 5, 6);
    }

    
    @Test
    public void testReadAll() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("simple");
        set.add(2);
        set.add(0);
        set.add(1);
        set.add(5);
        
        assertThat(set.readAll()).containsExactly(0, 1, 2, 5);
    }
    
    @Test
    public void testIteratorNextNext() {
        RPriorityQueue<String> list = redisson.getPriorityQueue("simple");
        list.add("1");
        list.add("4");

        Iterator<String> iter = list.iterator();
        Assertions.assertEquals("1", iter.next());
        Assertions.assertEquals("4", iter.next());
        Assertions.assertFalse(iter.hasNext());
    }
    
    @Test
    public void testIteratorRemove() {
        RPriorityQueue<String> list = redisson.getPriorityQueue("list");
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

        assertThat(list).contains("1", "4", "5", "3");

        int iteration = 0;
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            iterator.next();
            iterator.remove();
            iteration++;
        }

        Assertions.assertEquals(4, iteration);

        Assertions.assertEquals(0, list.size());
        Assertions.assertTrue(list.isEmpty());
    }
    
    @Test
    public void testIteratorSequence() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        for (int i = 0; i < 1000; i++) {
            set.add(Integer.valueOf(i));
        }

        Queue<Integer> setCopy = new PriorityQueue<Integer>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Integer.valueOf(i));
        }
        
        checkIterator(set, setCopy);
    }

    private void checkIterator(Queue<Integer> set, Queue<Integer> setCopy) {
        for (Integer value : set) {
            if (!setCopy.remove(value)) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, setCopy.size());
    }
    
    @Test
    public void testTrySetComparator() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");

        boolean setRes = set.trySetComparator(Collections.reverseOrder());
        Assertions.assertTrue(setRes);
        Assertions.assertTrue(set.add(1));
        Assertions.assertTrue(set.add(2));
        Assertions.assertTrue(set.add(3));
        Assertions.assertTrue(set.add(4));
        Assertions.assertTrue(set.add(5));
        assertThat(set).containsExactly(5, 4, 3, 2, 1);

        boolean setRes2 = set.trySetComparator(Collections.reverseOrder(Collections.reverseOrder()));
        Assertions.assertFalse(setRes2);
        assertThat(set).containsExactly(5, 4, 3, 2, 1);

        set.clear();
        boolean setRes3 = set.trySetComparator(Collections.reverseOrder(Collections.reverseOrder()));
        Assertions.assertTrue(setRes3);
        set.add(3);
        set.add(1);
        set.add(2);
        assertThat(set).containsExactly(1, 2, 3);
    }


    @Test
    public void testSort() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        Assertions.assertTrue(set.add(2));
        Assertions.assertTrue(set.add(3));
        Assertions.assertTrue(set.add(1));
        Assertions.assertTrue(set.add(4));
        Assertions.assertTrue(set.add(10));
        Assertions.assertTrue(set.add(-1));
        Assertions.assertTrue(set.add(0));

        assertThat(set).containsExactly(-1, 0, 1, 2, 3, 4, 10);

        Assertions.assertEquals(-1, (int)set.peek());
    }

    @Test
    public void testRemove() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        set.add(5);
        set.add(3);
        set.add(1);
        set.add(2);
        set.add(4);
        set.add(1);

        Assertions.assertFalse(set.remove(0));
        Assertions.assertTrue(set.remove(3));
        Assertions.assertTrue(set.remove(1));

        assertThat(set).containsExactly(1, 2, 4, 5);
    }

    @Test
    public void testRetainAll() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assertions.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Assertions.assertEquals(2, set.size());
    }

    @Test
    public void testContainsAll() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assertions.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assertions.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
    }

    @Test
    public void testToArray() {
        RPriorityQueue<String> set = redisson.getPriorityQueue("set");
        set.add("1");
        set.add("4");
        set.add("2");
        set.add("5");
        set.add("3");

        assertThat(set.toArray()).contains("1", "4", "2", "5", "3");

        String[] strs = set.toArray(new String[0]);
        assertThat(strs).contains("1", "4", "2", "5", "3");
    }

    @Test
    public void testContains() {
        RPriorityQueue<TestObject> set = redisson.getPriorityQueue("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assertions.assertTrue(set.contains(new TestObject("2", "3")));
        Assertions.assertTrue(set.contains(new TestObject("1", "2")));
        Assertions.assertFalse(set.contains(new TestObject("1", "9")));
    }

    @Test
    public void testDuplicates() {
        RPriorityQueue<TestObject> set = redisson.getPriorityQueue("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("5", "6"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("3", "4"));

        Assertions.assertEquals(5, set.size());
        
        assertThat(set).containsExactly(new TestObject("1", "2"), new TestObject("1", "2"),
                new TestObject("2", "3"), new TestObject("3", "4"), new TestObject("5", "6"));
    }

    @Test
    public void testSize() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(5);

        Assertions.assertEquals(7, set.size());
    }


}
