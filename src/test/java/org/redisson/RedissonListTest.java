package org.redisson;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.util.*;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RList;

public class RedissonListTest extends BaseTest {

    @Test
    public void testAddAllAsync() {
        final RList<Long> list = redisson.getList("list");
        list.addAllAsync(Arrays.asList(1L, 2L, 3L)).addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                list.addAllAsync(Arrays.asList(1L, 24L, 3L));
            }
        });

        Assert.assertThat(list, Matchers.contains(1L, 2L, 3L, 1L, 24L, 3L));
    }
    
    @Test
    public void testAddAsync() {
        final RList<Long> list = redisson.getList("list");
        list.addAsync(1L).addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                list.addAsync(2L);
            }
        }).awaitUninterruptibly();

        Assert.assertThat(list, Matchers.contains(1L, 2L));
    }
    
    @Test
    public void testLong() {
        List<Long> list = redisson.getList("list");
        list.add(1L);
        list.add(2L);

        Assert.assertThat(list, Matchers.contains(1L, 2L));
    }

    @Test(expected = IllegalStateException.class)
    public void testListIteratorSetListFail() {
        List<Integer> list = new ArrayList<Integer>();
        list.add(1);

        ListIterator<Integer> iterator = list.listIterator();

        iterator.next();
        iterator.add(2);
        iterator.set(3);
    }

    @Test(expected = IllegalStateException.class)
    public void testListIteratorSetFail() {
        List<Integer> list = redisson.getList("list");
        list.add(1);

        ListIterator<Integer> iterator = list.listIterator();

        iterator.next();
        iterator.add(2);
        iterator.set(3);
    }

    @Test
    public void testListIteratorGetSetList() {
        List<Integer> list = new ArrayList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        ListIterator<Integer> iterator = list.listIterator();

        Assert.assertFalse(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.next());
        iterator.set(3);
        Assert.assertThat(list, Matchers.contains(3, 2, 3, 4));
        Assert.assertTrue(2 == iterator.next());
        iterator.add(31);
        Assert.assertThat(list, Matchers.contains(3, 2, 31, 3, 4));
        Assert.assertTrue(3 == iterator.next());
        Assert.assertTrue(4 == iterator.next());
        Assert.assertFalse(iterator.hasNext());
        iterator.add(71);
        Assert.assertThat(list, Matchers.contains(3, 2, 31, 3, 4, 71));
        iterator.add(8);
        Assert.assertThat(list, Matchers.contains(3, 2, 31, 3, 4, 71, 8));
    }

    @Test
    public void testListIteratorGetSet() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        ListIterator<Integer> iterator = list.listIterator();

        Assert.assertFalse(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.next());
        iterator.set(3);
        Assert.assertThat(list, Matchers.contains(3, 2, 3, 4));
        Assert.assertTrue(2 == iterator.next());
        iterator.add(31);
        Assert.assertThat(list, Matchers.contains(3, 2, 31, 3, 4));
        Assert.assertTrue(3 == iterator.next());
        Assert.assertTrue(4 == iterator.next());
        Assert.assertFalse(iterator.hasNext());
        iterator.add(71);
        Assert.assertThat(list, Matchers.contains(3, 2, 31, 3, 4, 71));
        iterator.add(8);
        Assert.assertThat(list, Matchers.contains(3, 2, 31, 3, 4, 71, 8));
    }

    @Test
    public void testListIteratorPreviousList() {
        List<Integer> list = new LinkedList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(0);
        list.add(7);
        list.add(8);
        list.add(0);
        list.add(10);

        ListIterator<Integer> iterator = list.listIterator();

        Assert.assertFalse(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.next());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.previous());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(1 == iterator.next());

        Assert.assertTrue(2 == iterator.next());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(3 == iterator.next());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(3 == iterator.previous());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(2 == iterator.previous());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.previous());
        Assert.assertFalse(iterator.hasPrevious());
    }

    @Test
    public void testListIteratorIndexList() {
        List<Integer> list = new LinkedList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(0);
        list.add(7);
        list.add(8);
        list.add(0);
        list.add(10);

        ListIterator<Integer> iterator = list.listIterator();

        Assert.assertFalse(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.next());
        Assert.assertTrue(1 == iterator.nextIndex());
        Assert.assertTrue(0 == iterator.previousIndex());
        Assert.assertTrue(2 == iterator.next());
        Assert.assertTrue(3 == iterator.next());
        Assert.assertTrue(4 == iterator.next());
        Assert.assertTrue(5 == iterator.next());
        Assert.assertTrue(0 == iterator.next());
        Assert.assertTrue(7 == iterator.next());
        Assert.assertTrue(8 == iterator.next());
        Assert.assertTrue(0 == iterator.next());
        Assert.assertTrue(10 == iterator.next());
        Assert.assertTrue(9 == iterator.previousIndex());
        Assert.assertTrue(10 == iterator.nextIndex());
    }

    @Test
    public void testListIteratorIndex() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(0);
        list.add(7);
        list.add(8);
        list.add(0);
        list.add(10);

        ListIterator<Integer> iterator = list.listIterator();

        Assert.assertFalse(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.next());
        Assert.assertTrue(1 == iterator.nextIndex());
        Assert.assertTrue(0 == iterator.previousIndex());
        Assert.assertTrue(2 == iterator.next());
        Assert.assertTrue(3 == iterator.next());
        Assert.assertTrue(4 == iterator.next());
        Assert.assertTrue(5 == iterator.next());
        Assert.assertTrue(0 == iterator.next());
        Assert.assertTrue(7 == iterator.next());
        Assert.assertTrue(8 == iterator.next());
        Assert.assertTrue(0 == iterator.next());
        Assert.assertTrue(10 == iterator.next());
        Assert.assertTrue(9 == iterator.previousIndex());
        Assert.assertTrue(10 == iterator.nextIndex());
    }

    @Test
    public void testListIteratorPrevious() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(0);
        list.add(7);
        list.add(8);
        list.add(0);
        list.add(10);

        ListIterator<Integer> iterator = list.listIterator();

        Assert.assertFalse(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.next());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.previous());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(1 == iterator.next());

        Assert.assertTrue(2 == iterator.next());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(3 == iterator.next());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(3 == iterator.previous());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(2 == iterator.previous());
        Assert.assertTrue(iterator.hasPrevious());
        Assert.assertTrue(1 == iterator.previous());
        Assert.assertFalse(iterator.hasPrevious());
    }

    @Test
    public void testLastIndexOf2() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(0);
        list.add(7);
        list.add(8);
        list.add(0);
        list.add(10);

        int index = list.lastIndexOf(3);
        Assert.assertEquals(2, index);
    }

    @Test
    public void testLastIndexOf1() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(3);
        list.add(7);
        list.add(8);
        list.add(0);
        list.add(10);

        int index = list.lastIndexOf(3);
        Assert.assertEquals(5, index);
    }

    @Test
    public void testLastIndexOf() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(3);
        list.add(7);
        list.add(8);
        list.add(3);
        list.add(10);

        int index = list.lastIndexOf(3);
        Assert.assertEquals(8, index);
    }

    @Test
    public void testLastIndexOfList() {
        List<Integer> list = new LinkedList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(3);
        list.add(7);
        list.add(8);
        list.add(3);
        list.add(10);

        int index = list.lastIndexOf(3);
        Assert.assertEquals(8, index);
    }

    @Test
    public void testSubListList() {
        List<Integer> list = new LinkedList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(6);
        list.add(7);
        list.add(8);
        list.add(9);
        list.add(10);

        List<Integer> subList = list.subList(3, 7);
        Assert.assertThat(subList, Matchers.contains(4, 5, 6, 7));
    }


    @Test
    public void testSubList() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(6);
        list.add(7);
        list.add(8);
        list.add(9);
        list.add(10);

        List<Integer> subList = list.subList(3, 7);
        Assert.assertThat(subList, Matchers.contains(4, 5, 6, 7));
    }

    @Test
    public void testIndexOf() {
        List<Integer> list = redisson.getList("list");
        for (int i = 1; i < 200; i++) {
            list.add(i);
        }

        Assert.assertTrue(55 == list.indexOf(56));
        Assert.assertTrue(99 == list.indexOf(100));
        Assert.assertTrue(-1 == list.indexOf(200));
        Assert.assertTrue(-1 == list.indexOf(0));
    }


    @Test
    public void testRemove() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Integer val = list.remove(0);
        Assert.assertTrue(1 == val);

        Assert.assertThat(list, Matchers.contains(2, 3, 4, 5));
    }

    @Test
    public void testSet() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.set(4, 6);

        Assert.assertThat(list, Matchers.contains(1, 2, 3, 4, 6));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetFail() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.set(5, 6);
    }

    @Test
    public void testSetList() {
        List<Integer> list = new LinkedList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.set(4, 6);

        Assert.assertThat(list, Matchers.contains(1, 2, 3, 4, 6));
    }


    @Test
    public void testRemoveAll() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.removeAll(Arrays.asList(3, 2, 10, 6));

        Assert.assertThat(list, Matchers.contains(1, 4, 5));

        list.removeAll(Arrays.asList(4));

        Assert.assertThat(list, Matchers.contains(1, 5));

        list.removeAll(Arrays.asList(1, 5, 1, 5));

        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void testRetainAll() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assert.assertTrue(list.retainAll(Arrays.asList(3, 2, 10, 6)));

        Assert.assertThat(list, Matchers.contains(2, 3));
        Assert.assertEquals(2, list.size());
    }

    @Test
    public void testRetainAllEmpty() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assert.assertTrue(list.retainAll(Collections.<Integer>emptyList()));
        Assert.assertEquals(0, list.size());
    }

    @Test
    public void testRetainAllNoModify() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);

        Assert.assertFalse(list.retainAll(Arrays.asList(1, 2))); // nothing changed
        Assert.assertThat(list, Matchers.contains(1, 2));
    }


    @Test
    public void testAddAllIndex() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.addAll(2, Arrays.asList(7, 8, 9));

        Assert.assertThat(list, Matchers.contains(1, 2, 7, 8, 9, 3, 4, 5));

        list.addAll(list.size()-1, Arrays.asList(9, 1, 9));

        Assert.assertThat(list, Matchers.contains(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5));

        list.addAll(list.size(), Arrays.asList(0, 5));

        Assert.assertThat(list, Matchers.contains(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5));

        list.addAll(0, Arrays.asList(6, 7));

        Assert.assertThat(list, Matchers.contains(6,7,1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5));
    }

    @Test
    public void testAddAllIndexList() {
        List<Integer> list = new LinkedList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.addAll(2, Arrays.asList(7, 8, 9));

        list.addAll(list.size()-1, Arrays.asList(9, 1, 9));

        Assert.assertThat(list, Matchers.contains(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5));

        list.addAll(list.size(), Arrays.asList(0, 5));

        Assert.assertThat(list, Matchers.contains(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5));

        list.addAll(0, Arrays.asList(6,7));

        Assert.assertThat(list, Matchers.contains(6,7,1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5));
    }


    @Test
    public void testAddAll() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assert.assertTrue(list.addAll(Arrays.asList(7, 8, 9)));

        Assert.assertTrue(list.addAll(Arrays.asList(9, 1, 9)));

        Assert.assertThat(list, Matchers.contains(1, 2, 3, 4, 5, 7, 8, 9, 9, 1, 9));
    }

    @Test
    public void testAddAllEmpty() throws Exception {
        List<Integer> list = redisson.getList("list");
        Assert.assertFalse(list.addAll(Collections.<Integer>emptyList()));
        Assert.assertEquals(0, list.size());
    }

    @Test
    public void testContainsAll() {
        List<Integer> list = redisson.getList("list");
        for (int i = 0; i < 200; i++) {
            list.add(i);
        }

        Assert.assertTrue(list.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(list.containsAll(Arrays.asList(30, 711, 11)));
    }

    @Test
    public void testToArray() {
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        Assert.assertArrayEquals(list.toArray(), new Object[]{"1", "4", "2", "5", "3"});

        String[] strs = list.toArray(new String[0]);
        Assert.assertArrayEquals(strs, new String[]{"1", "4", "2", "5", "3"});
    }


    @Test
    public void testIteratorRemove() {
        List<String> list = redisson.getList("list");
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

        Assert.assertThat(list, Matchers.contains("1", "4", "5", "3"));

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
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        checkIterator(list);
        // to test "memory effect" absence
        checkIterator(list);
    }

    private void checkIterator(List<String> list) {
        int iteration = 0;
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            String value = iterator.next();
            String val = list.get(iteration);
            Assert.assertEquals(val, value);
            iteration++;
        }

        Assert.assertEquals(list.size(), iteration);
    }


    @Test
    public void testContains() {
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        Assert.assertTrue(list.contains("3"));
        Assert.assertFalse(list.contains("31"));
        Assert.assertTrue(list.contains("1"));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetFail2() {
        List<String> list = redisson.getList("list");

        list.get(0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetFail() {
        List<String> list = redisson.getList("list");

        list.get(0);
    }

    @Test
    public void testAddGet() {
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        String val1 = list.get(0);
        Assert.assertEquals("1", val1);

        String val2 = list.get(3);
        Assert.assertEquals("5", val2);
    }

    @Test
    public void testDuplicates() {
        List<TestObject> list = redisson.getList("list");

        list.add(new TestObject("1", "2"));
        list.add(new TestObject("1", "2"));
        list.add(new TestObject("2", "3"));
        list.add(new TestObject("3", "4"));
        list.add(new TestObject("5", "6"));

        Assert.assertEquals(5, list.size());
    }

    @Test
    public void testSize() {
        List<String> list = redisson.getList("list");

        list.add("1");
        list.add("2");
        list.add("3");
        list.add("4");
        list.add("5");
        list.add("6");
        Assert.assertThat(list, Matchers.contains("1", "2", "3", "4", "5", "6"));

        list.remove("2");
        Assert.assertThat(list, Matchers.contains("1", "3", "4", "5", "6"));

        list.remove("4");
        Assert.assertThat(list, Matchers.contains("1", "3", "5", "6"));
    }

    @Test
    public void testCodec() {
        List<Object> list = redisson.getList("list");
        list.add(1);
        list.add(2L);
        list.add("3");
        list.add("e");

        Assert.assertThat(list, Matchers.<Object>contains(1, 2L, "3", "e"));
    }
}
