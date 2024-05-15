package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.redisson.api.SortOrder;
import org.redisson.api.listener.ListAddListener;
import org.redisson.client.RedisException;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RedissonListTest extends RedisDockerTest {

    @Test
    public void testAddListener() {
        testWithParams(redisson -> {
            RList<Integer> al = redisson.getList("name");
            CountDownLatch latch = new CountDownLatch(1);
            al.addListener(new ListAddListener() {
                @Override
                public void onListAdd(String name) {
                    latch.countDown();
                }
            });
            al.add(1);

            try {
                assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, NOTIFY_KEYSPACE_EVENTS, "El");
    }

    @Test
    public void testGet() {
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(6);
        
        assertThat(list.get(1, 2, 3)).containsSequence(2, 3, 4);
    }
    
    @Test
    public void testRange() {
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        assertThat(list.range(1)).containsExactly(1, 2);
        assertThat(list.range(1, 3)).containsExactly(2, 3, 4);
        
        list.delete();
        
        assertThat(list.range(0, 2)).isEmpty();
    }
    
    @Test
    public void testSortOrder() {
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        List<Integer> descSort = list.readSort(SortOrder.DESC);
        assertThat(descSort).containsExactly(3, 2, 1);

        List<Integer> ascSort = list.readSort(SortOrder.ASC);
        assertThat(ascSort).containsExactly(1, 2, 3);
    }
    
    @Test
    public void testSortOrderLimit() {
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        List<Integer> descSort = list.readSort(SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly(2, 1);

        List<Integer> ascSort = list.readSort(SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly(2, 3);
    }

    @Test
    public void testSortOrderByPattern() {
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        List<Integer> descSort = list.readSort("test*", SortOrder.DESC);
        assertThat(descSort).containsExactly(1, 2, 3);

        List<Integer> ascSort = list.readSort("test*", SortOrder.ASC);
        assertThat(ascSort).containsExactly(3, 2, 1);
    }
    
    @Test
    public void testSortOrderByPatternLimit() {
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        List<Integer> descSort = list.readSort("test*", SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly(2, 3);

        List<Integer> ascSort = list.readSort("test*", SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly(2, 1);
    }

    @Test
    public void testSortOrderByPatternGet() {
        RList<String> list = redisson.getList("list", StringCodec.INSTANCE);
        list.add("1");
        list.add("2");
        list.add("3");
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(1);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(3);
        
        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");
        
        Collection<String> descSort = list.readSort("test*", Arrays.asList("tester*"), SortOrder.DESC);
        assertThat(descSort).containsExactly("obj3", "obj2", "obj1");

        Collection<String> ascSort = list.readSort("test*", Arrays.asList("tester*"), SortOrder.ASC);
        assertThat(ascSort).containsExactly("obj1", "obj2", "obj3");
    }
    
    @Test
    public void testSortOrderByPatternGetLimit() {
        RList<String> list = redisson.getList("list", StringCodec.INSTANCE);
        list.add("1");
        list.add("2");
        list.add("3");
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(1);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(3);
        
        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");
        
        Collection<String> descSort = list.readSort("test*", Arrays.asList("tester*"), SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly("obj2", "obj1");

        Collection<String> ascSort = list.readSort("test*", Arrays.asList("tester*"), SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly("obj2", "obj3");
    }

    @Test
    public void testSortOrderAlpha(){
        RList<String> list = redisson.getList("list", StringCodec.INSTANCE);
        list.add("1");
        list.add("3");
        list.add("12");

        assertThat(list.readSortAlpha( SortOrder.ASC))
                .containsExactly("1", "12", "3");
        assertThat(list.readSortAlpha( SortOrder.DESC))
                .containsExactly("3", "12", "1");
    }

    @Test
    public void testSortOrderLimitAlpha(){
        RList<String> list = redisson.getList("list", StringCodec.INSTANCE);
        list.add("1");
        list.add("3");
        list.add("12");

        assertThat(list.readSortAlpha(SortOrder.DESC, 0, 2))
                .containsExactly("3", "12");
        assertThat(list.readSortAlpha(SortOrder.DESC, 1, 2))
                .containsExactly("12", "1");
    }

    @Test
    public void testSortOrderByPatternAlpha(){
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        Collection<Integer> descSort = list
                .readSortAlpha("test*", SortOrder.DESC);
        assertThat(descSort).containsExactly(2, 1, 3);

        Collection<Integer> ascSort = list
                .readSortAlpha("test*", SortOrder.ASC);
        assertThat(ascSort).containsExactly(3, 1, 2);
    }

    @Test
    public void testSortOrderByPatternAlphaLimit(){
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        Collection<Integer> descSort = list
                .readSortAlpha("test*", SortOrder.DESC,1, 2);
        assertThat(descSort).containsExactly(1, 3);

        Collection<Integer> ascSort = list
                .readSortAlpha("test*", SortOrder.ASC,1, 2);
        assertThat(ascSort).containsExactly(1, 2);
    }

    @Test
    public void testSortOrderByPatternGetAlpha() {
        RList<String> list = redisson.getList("list", StringCodec.INSTANCE);
        list.add("1");
        list.add("2");
        list.add("3");

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");

        Collection<String> descSort = list
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.DESC);
        assertThat(descSort).containsExactly("obj2", "obj1", "obj3");

        Collection<String> ascSort = list
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.ASC);
        assertThat(ascSort).containsExactly("obj3", "obj1", "obj2");
    }

    @Test
    public void testSortOrderByPatternGetAlphaLimit() {
        RList<String> list = redisson.getList("list", StringCodec.INSTANCE);
        list.add("1");
        list.add("2");
        list.add("3");

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");

        Collection<String> descSort = list
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.DESC,1,  2);
        assertThat(descSort).containsExactly("obj1", "obj3");

        Collection<String> ascSort = list
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.ASC,1,  2);
        assertThat(ascSort).containsExactly("obj1", "obj2");
    }

    @Test
    public void testSortTo() {
        RList<String> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add("1");
        list.add("2");
        list.add("3");
        
        assertThat(list.sortTo("test3", SortOrder.DESC)).isEqualTo(3);
        RList<String> list2 = redisson.getList("test3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("3", "2", "1");
        
        assertThat(list.sortTo("test4", SortOrder.ASC)).isEqualTo(3);
        RList<String> list3 = redisson.getList("test4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("1", "2", "3");

    }

    @Test
    public void testSortToLimit() {
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        assertThat(list.sortTo("test3", SortOrder.DESC, 1, 2)).isEqualTo(2);
        RList<String> list2 = redisson.getList("test3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("2", "1");
        
        assertThat(list.sortTo("test4", SortOrder.ASC, 1, 2)).isEqualTo(2);
        RList<String> list3 = redisson.getList("test4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("2", "3");
    }

    @Test
    public void testSortToByPattern() {
        RList<Integer> list = redisson.getList("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        assertThat(list.sortTo("tester3", "test*", SortOrder.DESC, 1, 2)).isEqualTo(2);
        RList<String> list2 = redisson.getList("tester3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("2", "3");
        
        assertThat(list.sortTo("tester4", "test*", SortOrder.ASC, 1, 2)).isEqualTo(2);
        RList<String> list3 = redisson.getList("tester4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("2", "1");
    }

    @Test
    public void testAddBefore() {
        RList<String> list = redisson.getList("list");
        list.add("1");
        list.add("2");
        list.add("3");
        
        assertThat(list.addBefore("2", "0")).isEqualTo(4);

        assertThat(list).containsExactly("1", "0", "2", "3");        
    }
    
    @Test
    public void testAddAfter() {
        RList<String> list = redisson.getList("list");
        list.add("1");
        list.add("2");
        list.add("3");
        
        assertThat(list.addAfter("2", "0")).isEqualTo(4);

        assertThat(list).containsExactly("1", "2", "0", "3");        
    }

    
    @Test
    public void testTrim() {
        RList<String> list = redisson.getList("list1");
        list.add("1");
        list.add("2");
        list.add("3");
        list.add("4");
        list.add("5");
        list.add("6");

        list.trim(0, 3);
        assertThat(list).containsExactly("1", "2", "3", "4");
    }

    @Test
    public void testAddAllBigList() {
        RList<String> list = redisson.getList("list1");
        List<String> newList = new ArrayList<String>();
        for (int i = 0; i < 10000; i++) {
            newList.add("" + i);
        }
        list.addAll(newList);

        list.add(3, "123123");
    }

    @Test
    public void testEquals() {
        RList<String> list1 = redisson.getList("list1");
        list1.add("1");
        list1.add("2");
        list1.add("3");

        RList<String> list2 = redisson.getList("list2");
        list2.add("1");
        list2.add("2");
        list2.add("3");

        RList<String> list3 = redisson.getList("list3");
        list3.add("0");
        list3.add("2");
        list3.add("3");

        Assertions.assertEquals(list1, list2);
        Assertions.assertNotEquals(list1, list3);
    }

    @Test
    public void testHashCode() {
        RList<String> list = redisson.getList("list");
        list.add("a");
        list.add("b");
        list.add("c");

        Assertions.assertEquals(126145, list.hashCode());
    }

    @Test
    public void testAddByIndex() {
        RList<String> test2 = redisson.getList("test2");
        test2.add("foo");
        test2.add(0, "bar");

        assertThat(test2).containsExactly("bar", "foo");
    }

    @Test
    public void testLong() {
        List<Long> list = redisson.getList("list");
        list.add(1L);
        list.add(2L);

        assertThat(list).containsExactly(1L, 2L);
    }

    @Test
    public void testListIteratorSetListFail() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            List<Integer> list = new ArrayList<Integer>();
            list.add(1);

            ListIterator<Integer> iterator = list.listIterator();

            iterator.next();
            iterator.add(2);
            iterator.set(3);
        });
    }

    @Test
    public void testListIteratorSetFail() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            List<Integer> list = redisson.getList("list");
            list.add(1);

            ListIterator<Integer> iterator = list.listIterator();

            iterator.next();
            iterator.add(2);
            iterator.set(3);
        });
    }

    @Test
    public void testListIteratorSetFail2() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            List<Integer> list = redisson.getList("simpleList");
            list.add(1);

            ListIterator<Integer> iterator = list.listIterator();

            iterator.hasNext();
            iterator.set(3);
        });
    }

    @Test
    public void testListIteratorOK() {
        List<Integer> list = redisson.getList("simpleList");
        list.add(1);

        ListIterator<Integer> iterator = list.listIterator();

        iterator.next();
        iterator.set(3);
        iterator.set(4);
    }

    @Test
    public void testListIteratorGetSetList() {
        List<Integer> list = new ArrayList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        ListIterator<Integer> iterator = list.listIterator();

        Assertions.assertFalse(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.next());
        iterator.set(3);
        assertThat(list).containsExactly(3, 2, 3, 4);
        Assertions.assertTrue(2 == iterator.next());
        iterator.add(31);
        assertThat(list).containsExactly(3, 2, 31, 3, 4);
        Assertions.assertTrue(3 == iterator.next());
        Assertions.assertTrue(4 == iterator.next());
        Assertions.assertFalse(iterator.hasNext());
        iterator.add(71);
        assertThat(list).containsExactly(3, 2, 31, 3, 4, 71);
        iterator.add(8);
        assertThat(list).containsExactly(3, 2, 31, 3, 4, 71, 8);
    }

    @Test
    public void testListIteratorGetSet() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        ListIterator<Integer> iterator = list.listIterator();

        Assertions.assertFalse(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.next());
        iterator.set(3);
        assertThat(list).containsExactly(3, 2, 3, 4);
        Assertions.assertTrue(2 == iterator.next());
        iterator.add(31);
        assertThat(list).containsExactly(3, 2, 31, 3, 4);
        Assertions.assertTrue(3 == iterator.next());
        Assertions.assertTrue(4 == iterator.next());
        Assertions.assertFalse(iterator.hasNext());
        iterator.add(71);
        assertThat(list).containsExactly(3, 2, 31, 3, 4, 71);
        iterator.add(8);
        assertThat(list).containsExactly(3, 2, 31, 3, 4, 71, 8);
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

        Assertions.assertFalse(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.next());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.previous());
        Assertions.assertTrue(iterator.hasNext());
        Assertions.assertTrue(1 == iterator.next());

        Assertions.assertTrue(2 == iterator.next());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(3 == iterator.next());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(3 == iterator.previous());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(2 == iterator.previous());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.previous());
        Assertions.assertFalse(iterator.hasPrevious());
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

        Assertions.assertFalse(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.next());
        Assertions.assertTrue(1 == iterator.nextIndex());
        Assertions.assertTrue(0 == iterator.previousIndex());
        Assertions.assertTrue(2 == iterator.next());
        Assertions.assertTrue(3 == iterator.next());
        Assertions.assertTrue(4 == iterator.next());
        Assertions.assertTrue(5 == iterator.next());
        Assertions.assertTrue(0 == iterator.next());
        Assertions.assertTrue(7 == iterator.next());
        Assertions.assertTrue(8 == iterator.next());
        Assertions.assertTrue(0 == iterator.next());
        Assertions.assertTrue(10 == iterator.next());
        Assertions.assertTrue(9 == iterator.previousIndex());
        Assertions.assertTrue(10 == iterator.nextIndex());
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

        Assertions.assertFalse(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.next());
        Assertions.assertTrue(1 == iterator.nextIndex());
        Assertions.assertTrue(0 == iterator.previousIndex());
        Assertions.assertTrue(2 == iterator.next());
        Assertions.assertTrue(3 == iterator.next());
        Assertions.assertTrue(4 == iterator.next());
        Assertions.assertTrue(5 == iterator.next());
        Assertions.assertTrue(0 == iterator.next());
        Assertions.assertTrue(7 == iterator.next());
        Assertions.assertTrue(8 == iterator.next());
        Assertions.assertTrue(0 == iterator.next());
        Assertions.assertTrue(10 == iterator.next());
        Assertions.assertTrue(9 == iterator.previousIndex());
        Assertions.assertTrue(10 == iterator.nextIndex());
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

        Assertions.assertFalse(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.next());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.previous());
        Assertions.assertTrue(iterator.hasNext());
        Assertions.assertTrue(1 == iterator.next());

        Assertions.assertTrue(2 == iterator.next());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(3 == iterator.next());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(3 == iterator.previous());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(2 == iterator.previous());
        Assertions.assertTrue(iterator.hasPrevious());
        Assertions.assertTrue(1 == iterator.previous());
        Assertions.assertFalse(iterator.hasPrevious());
    }

    @Test
    public void testLastIndexOfNone() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assertions.assertEquals(-1, list.lastIndexOf(10));
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
        Assertions.assertEquals(2, index);
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
        Assertions.assertEquals(5, index);
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
        Assertions.assertEquals(8, index);
    }

    @Test
    public void testSubListMiddle() {
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
        assertThat(subList.iterator()).toIterable().containsExactly(4, 5, 6, 7);

        subList.clear();
        assertThat(list).containsExactly(1, 2, 3, 8, 9, 10);
        assertThat(subList.size()).isZero();

        List<Integer> subList2 = list.subList(3, 6);
        assertThat(subList2.iterator()).toIterable().containsExactly(8, 9, 10);
    }

    @Test
    public void testSubListHead() {
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

        List<Integer> subList = list.subList(0, 3);
        assertThat(subList).containsExactly(1, 2, 3);

        subList.clear();
        assertThat(list).containsExactly(4, 5, 6, 7, 8, 9, 10);
        assertThat(subList.size()).isZero();

        List<Integer> subList2 = list.subList(0, 2);
        assertThat(subList2).containsExactly(4, 5);
    }

    @Test
    public void testSubListHeadIterator() {
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

        List<Integer> subList = list.subList(0, 3);
        assertThat(subList).containsExactly(1, 2, 3);

        for (Iterator<Integer> iterator = subList.iterator(); iterator.hasNext();) {
            Integer num = iterator.next();
            if (num == 2) {
                iterator.remove();
            }
        }
        assertThat(subList.size()).isEqualTo(2);
        assertThat(subList).containsExactly(1, 3);
    }

    @Test
    public void testSubListMiddleIterator() {
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
        assertThat(subList.iterator()).toIterable().containsExactly(4, 5, 6, 7);

        for (Iterator<Integer> iterator = subList.iterator(); iterator.hasNext();) {
            Integer num = iterator.next();
            if (num == 5) {
                iterator.remove();
            }
        }

        assertThat(subList.iterator()).toIterable().containsExactly(4, 6, 7);

        ListIterator<Integer> iterator = subList.listIterator();
        assertThat(iterator.hasPrevious()).isFalse();

        assertThat(subList.size()).isEqualTo(3);
    }

    @Test
    public void testIndexOf() {
        List<Integer> list = redisson.getList("list");
        for (int i = 1; i < 200; i++) {
            list.add(i);
        }

        Assertions.assertTrue(55 == list.indexOf(56));
        Assertions.assertTrue(99 == list.indexOf(100));
        Assertions.assertTrue(-1 == list.indexOf(200));
        Assertions.assertTrue(-1 == list.indexOf(0));
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
        assertThat(val).isEqualTo(1);

        assertThat(list).containsExactly(2, 3, 4, 5);

        Integer val1 = list.remove(2);
        assertThat(val1).isEqualTo(4);
        assertThat(list).containsExactly(2, 3, 5);
    }

    @Test
    public void testRemoveWithCount() {
        RList<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(3);
        list.add(4);

        assertThat(list.remove(1, 5)).isTrue();
        assertThat(list).containsExactly(2, 3, 3, 4);

        assertThat(list.remove(3, 5)).isTrue();
        assertThat(list).containsExactly(2, 4);
    }

    @Test
    public void testSubListRemove() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        list.add(6);
        list.add(7);
        list.add(8);

        List<Integer> subList = list.subList(2, 6);
        assertThat(subList.iterator()).toIterable().containsExactly(3, 4, 5, 6);
        assertThat(subList.size()).isEqualTo(4);
        Integer val = subList.remove(2);
        assertThat(val).isEqualTo(5);

        assertThat(subList.iterator()).toIterable().containsExactly(3, 4, 6);

        Integer val1 = subList.remove(2);
        assertThat(val1).isEqualTo(6);
        assertThat(subList.iterator()).toIterable().containsExactly(3, 4);
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

        assertThat(list).containsExactly(1, 2, 3, 4, 6);
    }

    @Test
    public void testSetFail() {
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            List<Integer> list = redisson.getList("list");
            list.add(1);
            list.add(2);
            list.add(3);
            list.add(4);
            list.add(5);

            list.set(5, 6);
        });
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

        assertThat(list).containsExactly(1, 2, 3, 4, 6);
    }


    @Test
    public void testRemoveAllEmpty() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assertions.assertFalse(list.removeAll(Collections.emptyList()));
        Assertions.assertFalse(Arrays.asList(1).removeAll(Collections.emptyList()));
    }

    @Test
    public void testRemoveAll() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assertions.assertFalse(list.removeAll(Collections.emptyList()));
        Assertions.assertTrue(list.removeAll(Arrays.asList(3, 2, 10, 6)));

        assertThat(list).containsExactly(1, 4, 5);

        Assertions.assertTrue(list.removeAll(Arrays.asList(4)));

        assertThat(list).containsExactly(1, 5);

        Assertions.assertTrue(list.removeAll(Arrays.asList(1, 5, 1, 5)));

        Assertions.assertTrue(list.isEmpty());
    }

    @Test
    public void testRetainAll() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assertions.assertTrue(list.retainAll(Arrays.asList(3, 2, 10, 6)));

        assertThat(list).containsExactly(2, 3);
        Assertions.assertEquals(2, list.size());
    }

    @Test
    public void testFastSet() {
        RList<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);

        list.fastSet(0, 3);
        Assertions.assertEquals(3, (int)list.get(0));
    }

    @Test
    public void testRetainAllEmpty() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assertions.assertTrue(list.retainAll(Collections.<Integer>emptyList()));
        Assertions.assertEquals(0, list.size());
    }

    @Test
    public void testRetainAllNoModify() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);

        Assertions.assertFalse(list.retainAll(Arrays.asList(1, 2))); // nothing changed
        assertThat(list).containsExactly(1, 2);
    }

    @Test
    public void testAddAllIndexError() {
        Assertions.assertThrows(RedisException.class, () -> {
            RList<Integer> list = redisson.getList("list");
            list.addAll(2, Arrays.asList(7, 8, 9));
        });
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

        assertThat(list).containsExactly(1, 2, 7, 8, 9, 3, 4, 5);

        list.addAll(list.size()-1, Arrays.asList(9, 1, 9));

        assertThat(list).containsExactly(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5);

        list.addAll(list.size(), Arrays.asList(0, 5));

        assertThat(list).containsExactly(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5);

        list.addAll(0, Arrays.asList(6, 7));

        assertThat(list).containsExactly(6,7,1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5);
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

        assertThat(list).containsExactly(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5);

        list.addAll(list.size(), Arrays.asList(0, 5));

        assertThat(list).containsExactly(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5);

        list.addAll(0, Arrays.asList(6,7));

        assertThat(list).containsExactly(6,7,1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5);
    }


    @Test
    public void testAddAll() {
        List<Integer> list = redisson.getList("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assertions.assertTrue(list.addAll(Arrays.asList(7, 8, 9)));

        Assertions.assertTrue(list.addAll(Arrays.asList(9, 1, 9)));

        assertThat(list).containsExactly(1, 2, 3, 4, 5, 7, 8, 9, 9, 1, 9);
    }

    @Test
    public void testAddAllEmpty() throws Exception {
        List<Integer> list = redisson.getList("list");
        Assertions.assertFalse(list.addAll(Collections.<Integer>emptyList()));
        Assertions.assertEquals(0, list.size());
    }

    @Test
    public void testContainsAll() {
        List<Integer> list = redisson.getList("list");
        for (int i = 0; i < 200; i++) {
            list.add(i);
        }

        Assertions.assertTrue(list.containsAll(Arrays.asList(30, 11)));
        Assertions.assertFalse(list.containsAll(Arrays.asList(30, 711, 11)));
        Assertions.assertTrue(list.containsAll(Arrays.asList(30)));
    }

    @Test
    public void testContainsAllEmpty() {
        List<Integer> list = redisson.getList("list");
        for (int i = 0; i < 200; i++) {
            list.add(i);
        }

        Assertions.assertTrue(list.containsAll(Collections.emptyList()));
        Assertions.assertTrue(Arrays.asList(1).containsAll(Collections.emptyList()));
    }

    @Test
    public void testToArray() {
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        Assertions.assertArrayEquals(list.toArray(), new Object[]{"1", "4", "2", "5", "3"});

        String[] strs = list.toArray(new String[0]);
        Assertions.assertArrayEquals(strs, new String[]{"1", "4", "2", "5", "3"});
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

        assertThat(list).containsExactly("1", "4", "5", "3");

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
            Assertions.assertEquals(val, value);
            iteration++;
        }

        Assertions.assertEquals(list.size(), iteration);
    }


    @Test
    public void testContains() {
        List<String> list = redisson.getList("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        Assertions.assertTrue(list.contains("3"));
        Assertions.assertFalse(list.contains("31"));
        Assertions.assertTrue(list.contains("1"));
    }

//    @Test(expected = IndexOutOfBoundsException.class)
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
        Assertions.assertEquals("1", val1);

        String val2 = list.get(3);
        Assertions.assertEquals("5", val2);
    }

    @Test
    public void testDuplicates() {
        List<TestObject> list = redisson.getList("list");

        list.add(new TestObject("1", "2"));
        list.add(new TestObject("1", "2"));
        list.add(new TestObject("2", "3"));
        list.add(new TestObject("3", "4"));
        list.add(new TestObject("5", "6"));

        Assertions.assertEquals(5, list.size());
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
        
        assertThat(list).containsExactly("1", "2", "3", "4", "5", "6");

        list.remove("2");
        
        assertThat(list).containsExactly("1", "3", "4", "5", "6");

        list.remove("4");
        
        assertThat(list).containsExactly("1", "3", "5", "6");
    }

    @Test
    public void testCodec() {
        List<Object> list = redisson.getList("list");
        list.add(1);
        list.add(2L);
        list.add("3");
        list.add("e");

        assertThat(list).containsExactly(1, 2L, "3", "e");
    }

    @Test
    public void testDistributedIterator() {
        RList<String> list = redisson.getList("list", StringCodec.INSTANCE);

        // populate list with elements
        List<String> strings = IntStream.range(0, 128).mapToObj(i -> i + "").collect(Collectors.toList());
        list.addAll(strings);

        Iterator<String> stringIterator = list.distributedIterator("iterator_{list}", 10);

        // read some elements using iterator
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (stringIterator.hasNext()) {
                actual.add(stringIterator.next());
            }
        }

        // create another iterator instance using the same name
        RList<String> set2 = redisson.getList("list", StringCodec.INSTANCE);
        Iterator<String> stringIterator2 = set2.distributedIterator("iterator_{list}", 10);

        assertTrue(stringIterator2.hasNext());

        // read all remaining elements
        stringIterator2.forEachRemaining(actual::add);
        stringIterator.forEachRemaining(actual::add);

        assertThat(actual).containsAll(strings);
        assertThat(actual).hasSize(strings.size());
    }
}
