package org.redisson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class RedissonListTest extends BaseTest {

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
        Redisson redisson = Redisson.create();
        List<Integer> list = redisson.getList("list");
        list.add(1);

        ListIterator<Integer> iterator = list.listIterator();

        iterator.next();
        iterator.add(2);
        try {
            iterator.set(3);
        } finally {
            clear(list, redisson);
        }
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
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
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
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

    @Test
    public void testListIteratorPrevious() {
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

    @Test
    public void testLastIndexOf2() {
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

    @Test
    public void testLastIndexOf1() {
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

    @Test
    public void testLastIndexOf() {
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
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
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

    @Test
    public void testIndexOf() {
        Redisson redisson = Redisson.create();
        List<Integer> list = redisson.getList("list");
        for (int i = 1; i < 200; i++) {
            list.add(i);
        }

        Assert.assertTrue(55 == list.indexOf(56));
        Assert.assertTrue(99 == list.indexOf(100));
        Assert.assertTrue(-1 == list.indexOf(200));
        Assert.assertTrue(-1 == list.indexOf(0));

        clear(list, redisson);
    }


    @Test
    public void testRemove() {
        Redisson redisson = Redisson.create();
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Integer val = list.remove(0);
        Assert.assertTrue(1 == val);

        Assert.assertThat(list, Matchers.contains(2, 3, 4, 5));

        clear(list, redisson);
    }

    @Test
    public void testSet() {
        Redisson redisson = Redisson.create();
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.set(4, 6);

        Assert.assertThat(list, Matchers.contains(1, 2, 3, 4, 6));
        clear(list, redisson);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetFail() {
        Redisson redisson = Redisson.create();
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        try {
            list.set(5, 6);
        } finally {
            clear(list, redisson);
        }
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
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }


    @Test
    public void testAddAllIndex() {
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

    @Test
    public void testAddAllIndexList() {
        List<Integer> list = new LinkedList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.addAll(2, Arrays.asList(7,8,9));

        list.addAll(list.size()-1, Arrays.asList(9, 1, 9));

        Assert.assertThat(list, Matchers.contains(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5));

        list.addAll(list.size(), Arrays.asList(0, 5));

        Assert.assertThat(list, Matchers.contains(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5));
    }


    @Test
    public void testAddAll() {
        Redisson redisson = Redisson.create();
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        list.addAll(Arrays.asList(7,8,9));

        list.addAll(Arrays.asList(9, 1, 9));

        Assert.assertThat(list, Matchers.contains(1, 2, 3, 4, 5, 7, 8, 9, 9, 1, 9));

        clear(list, redisson);
    }

    @Test
    public void testContainsAll() {
        Redisson redisson = Redisson.create();
        List<Integer> list = redisson.getList("list");
        for (int i = 0; i < 200; i++) {
            list.add(i);
        }

        Assert.assertTrue(list.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(list.containsAll(Arrays.asList(30, 711, 11)));

        clear(list, redisson);
    }

    @Test
    public void testToArray() {
        Redisson redisson = Redisson.create();
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        Assert.assertArrayEquals(list.toArray(), new Object[] {"1", "4", "2", "5", "3"});

        String[] strs = list.toArray(new String[0]);
        Assert.assertArrayEquals(strs, new String[] {"1", "4", "2", "5", "3"});

        clear(list, redisson);
    }


    @Test
    public void testIteratorRemove() {
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

    @Test
    public void testIteratorSequence() {
        Redisson redisson = Redisson.create();
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        checkIterator(list);
        // to test "memory effect" absence
        checkIterator(list);

        clear(list, redisson);
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
        Redisson redisson = Redisson.create();
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        Assert.assertTrue(list.contains("3"));
        Assert.assertFalse(list.contains("31"));
        Assert.assertTrue(list.contains("1"));

        clear(list, redisson);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetFail2() {
        Redisson redisson = Redisson.create();
        List<String> list = redisson.getList("list");

        list.get(0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetFail() {
        Redisson redisson = Redisson.create();
        List<String> list = redisson.getList("list");

        list.get(0);
    }

    @Test
    public void testAddGet() {
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

    @Test
    public void testDuplicates() {
        Redisson redisson = Redisson.create();
        List<TestObject> list = redisson.getList("list");

        list.add(new TestObject("1", "2"));
        list.add(new TestObject("1", "2"));
        list.add(new TestObject("2", "3"));
        list.add(new TestObject("3", "4"));
        list.add(new TestObject("5", "6"));

        Assert.assertEquals(5, list.size());

        clear(list, redisson);
    }

    @Test
    public void testSize() {
        Redisson redisson = Redisson.create();
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

        clear(list, redisson);
    }

}
