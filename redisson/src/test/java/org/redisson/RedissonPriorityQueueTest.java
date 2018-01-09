package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RPriorityQueue;

public class RedissonPriorityQueueTest extends BaseTest {

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
        Assert.assertEquals("1", iter.next());
        Assert.assertEquals("4", iter.next());
        Assert.assertFalse(iter.hasNext());
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

        Assert.assertEquals(4, iteration);

        Assert.assertEquals(0, list.size());
        Assert.assertTrue(list.isEmpty());
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
        for (Iterator<Integer> iterator = set.iterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!setCopy.remove(value)) {
                Assert.fail();
            }
        }

        Assert.assertEquals(0, setCopy.size());
    }
    
    @Test
    public void testTrySetComparator() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");

        boolean setRes = set.trySetComparator(Collections.reverseOrder());
        Assert.assertTrue(setRes);
        Assert.assertTrue(set.add(1));
        Assert.assertTrue(set.add(2));
        Assert.assertTrue(set.add(3));
        Assert.assertTrue(set.add(4));
        Assert.assertTrue(set.add(5));
        assertThat(set).containsExactly(5, 4, 3, 2, 1);

        boolean setRes2 = set.trySetComparator(Collections.reverseOrder(Collections.reverseOrder()));
        Assert.assertFalse(setRes2);
        assertThat(set).containsExactly(5, 4, 3, 2, 1);

        set.clear();
        boolean setRes3 = set.trySetComparator(Collections.reverseOrder(Collections.reverseOrder()));
        Assert.assertTrue(setRes3);
        set.add(3);
        set.add(1);
        set.add(2);
        assertThat(set).containsExactly(1, 2, 3);
    }


    @Test
    public void testSort() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        Assert.assertTrue(set.add(2));
        Assert.assertTrue(set.add(3));
        Assert.assertTrue(set.add(1));
        Assert.assertTrue(set.add(4));
        Assert.assertTrue(set.add(10));
        Assert.assertTrue(set.add(-1));
        Assert.assertTrue(set.add(0));

        assertThat(set).containsExactly(-1, 0, 1, 2, 3, 4, 10);

        Assert.assertEquals(-1, (int)set.peek());
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

        Assert.assertFalse(set.remove(0));
        Assert.assertTrue(set.remove(3));
        Assert.assertTrue(set.remove(1));

        assertThat(set).containsExactly(1, 2, 4, 5);
    }

    @Test
    public void testRetainAll() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Assert.assertEquals(2, set.size());
    }

    @Test
    public void testContainsAll() {
        RPriorityQueue<Integer> set = redisson.getPriorityQueue("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
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

        Assert.assertTrue(set.contains(new TestObject("2", "3")));
        Assert.assertTrue(set.contains(new TestObject("1", "2")));
        Assert.assertFalse(set.contains(new TestObject("1", "9")));
    }

    @Test
    public void testDuplicates() {
        RPriorityQueue<TestObject> set = redisson.getPriorityQueue("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("5", "6"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("3", "4"));

        Assert.assertEquals(5, set.size());
        
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

        Assert.assertEquals(7, set.size());
    }


}
