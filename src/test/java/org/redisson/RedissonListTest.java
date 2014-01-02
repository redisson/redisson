package org.redisson;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class RedissonListTest {

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

        clear(list);
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

        clear(list);
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
        clear(list);
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
            clear(list);
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

        clear(list);
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

        clear(list);
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

        clear(list);
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

        clear(list);
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

        clear(list);
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

        clear(list);
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

        Assert.assertTrue(Arrays.equals(list.toArray(), new Object[] {"1", "4", "2", "5", "3"}));

        String[] strs = list.toArray(new String[0]);
        Assert.assertTrue(Arrays.equals(strs, new String[] {"1", "4", "2", "5", "3"}));

        clear(list);
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

        clear(list);
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

        clear(list);
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

        clear(list);
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

        clear(list);
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

        clear(list);
    }

    private void clear(List<?> list) {
        list.clear();
        Assert.assertEquals(0, list.size());
    }


}
