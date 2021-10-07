package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.TestObject;
import org.redisson.api.RListRx;
import org.redisson.client.RedisException;

public class RedissonListRxTest extends BaseRxTest {

    @Test
    public void testAddByIndex() {
        RListRx<String> test2 = redisson.getList("test2");
        sync(test2.add("foo"));
        sync(test2.add(0, "bar"));

        assertThat(sync(test2)).containsExactly("bar", "foo");
    }

    @Test
    public void testAddAllReactive() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        RListRx<Integer> list2 = redisson.getList("list2");
        Assertions.assertEquals(true, sync(list2.addAll(list.iterator())));
        Assertions.assertEquals(5, sync(list2.size()).intValue());
    }

    @Test
    public void testAddAllWithIndex() throws InterruptedException {
        final RListRx<Long> list = redisson.getList("list");
        final CountDownLatch latch = new CountDownLatch(1);
        list.addAll(Arrays.asList(1L, 2L, 3L)).subscribe((Boolean element, Throwable error) -> {
            if (error != null) {
                Assertions.fail(error.getMessage());
                return;
            }
            
            list.addAll(Arrays.asList(1L, 24L, 3L)).subscribe((Boolean value, Throwable err) -> {
                if (err != null) {
                    Assertions.fail(err.getMessage());
                    return;
                }
                
                latch.countDown();
            });
        });

        latch.await();

        assertThat(sync(list)).containsExactly(1L, 2L, 3L, 1L, 24L, 3L);
    }

    @Test
    public void testAdd() throws InterruptedException {
        final RListRx<Long> list = redisson.getList("list");
        final CountDownLatch latch = new CountDownLatch(1);
        list.add(1L).subscribe((Boolean value, Throwable error) -> {
            if (error != null) {
                Assertions.fail(error.getMessage());
                return;
            }
            
            list.add(2L).subscribe((Boolean va, Throwable err) -> {
                if (err != null) {
                    Assertions.fail(err.getMessage());
                    return;
                }

                latch.countDown();
            });
        });

        latch.await();

        assertThat(sync(list)).containsExactly(1L, 2L);
    }

    @Test
    public void testLong() {
        RListRx<Long> list = redisson.getList("list");
        sync(list.add(1L));
        sync(list.add(2L));

        assertThat(sync(list)).containsExactly(1L, 2L);
    }

    @Test
    public void testListIteratorIndex() {
        RListRx<Integer> list = redisson.getList("list2");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));
        sync(list.add(0));
        sync(list.add(7));
        sync(list.add(8));
        sync(list.add(0));
        sync(list.add(10));

        Iterator<Integer> iterator = toIterator(list.iterator());

        Assertions.assertTrue(1 == iterator.next());
        Assertions.assertTrue(2 == iterator.next());
        Assertions.assertTrue(3 == iterator.next());
        Assertions.assertTrue(4 == iterator.next());
        Assertions.assertTrue(5 == iterator.next());
        Assertions.assertTrue(0 == iterator.next());
        Assertions.assertTrue(7 == iterator.next());
        Assertions.assertTrue(8 == iterator.next());
        Assertions.assertTrue(0 == iterator.next());
        Assertions.assertTrue(10 == iterator.next());
        Assertions.assertFalse(iterator.hasNext());
    }

    @Test
    public void testListIteratorPrevious() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));
        sync(list.add(0));
        sync(list.add(7));
        sync(list.add(8));
        sync(list.add(0));
        sync(list.add(10));

        Iterator<Integer> iterator = toIterator(list.descendingIterator());

        Assertions.assertTrue(10 == iterator.next());
        Assertions.assertTrue(0 == iterator.next());
        Assertions.assertTrue(8 == iterator.next());
        Assertions.assertTrue(7 == iterator.next());
        Assertions.assertTrue(0 == iterator.next());
        Assertions.assertTrue(5 == iterator.next());
        Assertions.assertTrue(4 == iterator.next());
        Assertions.assertTrue(3 == iterator.next());
        Assertions.assertTrue(2 == iterator.next());
        Assertions.assertTrue(1 == iterator.next());
        Assertions.assertFalse(iterator.hasNext());
    }

    @Test
    public void testLastIndexOfNone() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assertions.assertEquals(-1, sync(list.lastIndexOf(10)).intValue());
    }

    @Test
    public void testLastIndexOf2() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));
        sync(list.add(0));
        sync(list.add(7));
        sync(list.add(8));
        sync(list.add(0));
        sync(list.add(10));

        long index = sync(list.lastIndexOf(3));
        Assertions.assertEquals(2, index);
    }

    @Test
    public void testLastIndexOf1() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));
        sync(list.add(3));
        sync(list.add(7));
        sync(list.add(8));
        sync(list.add(0));
        sync(list.add(10));

        long index = sync(list.lastIndexOf(3));
        Assertions.assertEquals(5, index);
    }

    @Test
    public void testLastIndexOf() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));
        sync(list.add(3));
        sync(list.add(7));
        sync(list.add(8));
        sync(list.add(3));
        sync(list.add(10));

        int index = sync(list.lastIndexOf(3));
        Assertions.assertEquals(8, index);
    }

    @Test
    public void testIndexOf() {
        RListRx<Integer> list = redisson.getList("list");
        for (int i = 1; i < 200; i++) {
            sync(list.add(i));
        }

        Assertions.assertTrue(55 == sync(list.indexOf(56)));
        Assertions.assertTrue(99 == sync(list.indexOf(100)));
        Assertions.assertTrue(-1 == sync(list.indexOf(200)));
        Assertions.assertTrue(-1 == sync(list.indexOf(0)));
    }

    @Test
    public void testRemove() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Integer val = sync(list.remove(0));
        Assertions.assertTrue(1 == val);

        assertThat(sync(list)).containsExactly(2, 3, 4, 5);
    }

    @Test
    public void testSet() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        sync(list.set(4, 6));

        assertThat(sync(list)).containsExactly(1, 2, 3, 4, 6);
    }

    @Test
    public void testSetFail() throws InterruptedException {
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            RListRx<Integer> list = redisson.getList("list");
            sync(list.add(1));
            sync(list.add(2));
            sync(list.add(3));
            sync(list.add(4));
            sync(list.add(5));

            sync(list.set(5, 6));
        });
    }

    @Test
    public void testRemoveAllEmpty() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assertions.assertFalse(sync(list.removeAll(Collections.emptyList())));
        Assertions.assertFalse(Arrays.asList(1).removeAll(Collections.emptyList()));
    }

    @Test
    public void testRemoveAll() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assertions.assertFalse(sync(list.removeAll(Collections.emptyList())));
        Assertions.assertTrue(sync(list.removeAll(Arrays.asList(3, 2, 10, 6))));

        assertThat(sync(list)).containsExactly(1, 4, 5);

        Assertions.assertTrue(sync(list.removeAll(Arrays.asList(4))));

        assertThat(sync(list)).containsExactly(1, 5);

        Assertions.assertTrue(sync(list.removeAll(Arrays.asList(1, 5, 1, 5))));

        Assertions.assertEquals(0, sync(list.size()).longValue());
    }

    @Test
    public void testRetainAll() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assertions.assertTrue(sync(list.retainAll(Arrays.asList(3, 2, 10, 6))));

        assertThat(sync(list)).containsExactly(2, 3);
        Assertions.assertEquals(2, sync(list.size()).longValue());
    }

    @Test
    public void testFastSet() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));

        sync(list.fastSet(0, 3));
        Assertions.assertEquals(3, (int)sync(list.get(0)));
    }

    @Test
    public void testRetainAllEmpty() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assertions.assertTrue(sync(list.retainAll(Collections.<Integer>emptyList())));
        Assertions.assertEquals(0, sync(list.size()).intValue());
    }

    @Test
    public void testRetainAllNoModify() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));

        Assertions.assertFalse(sync(list.retainAll(Arrays.asList(1, 2)))); // nothing changed
        assertThat(sync(list)).containsExactly(1, 2);
    }

    @Test
    public void testAddAllIndexError() {
        Assertions.assertThrows(RedisException.class, () -> {
            RListRx<Integer> list = redisson.getList("list");
            sync(list.addAll(2, Arrays.asList(7, 8, 9)));
        });
    }

    @Test
    public void testAddAllIndex() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assertions.assertEquals(true, sync(list.addAll(2, Arrays.asList(7, 8, 9))));

        assertThat(sync(list)).containsExactly(1, 2, 7, 8, 9, 3, 4, 5);

        sync(list.addAll(sync(list.size())-1, Arrays.asList(9, 1, 9)));

        assertThat(sync(list)).containsExactly(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5);

        sync(list.addAll(sync(list.size()), Arrays.asList(0, 5)));

        assertThat(sync(list)).containsExactly(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5);

        Assertions.assertEquals(true, sync(list.addAll(0, Arrays.asList(6, 7))));

        assertThat(sync(list)).containsExactly(6,7,1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5);
    }

    @Test
    public void testAddAll() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assertions.assertEquals(true, sync(list.addAll(Arrays.asList(7, 8, 9))));

        Assertions.assertEquals(true, sync(list.addAll(Arrays.asList(9, 1, 9))));

        assertThat(sync(list)).containsExactly(1, 2, 3, 4, 5, 7, 8, 9, 9, 1, 9);
    }

    @Test
    public void testAddAllEmpty() {
        RListRx<Integer> list = redisson.getList("list");
        Assertions.assertEquals(false, sync(list.addAll(Collections.<Integer>emptyList())));
        Assertions.assertEquals(0, sync(list.size()).intValue());
    }

    @Test
    public void testContainsAll() {
        RListRx<Integer> list = redisson.getList("list");
        for (int i = 0; i < 200; i++) {
            sync(list.add(i));
        }

        Assertions.assertTrue(sync(list.containsAll(Arrays.asList(30, 11))));
        Assertions.assertFalse(sync(list.containsAll(Arrays.asList(30, 711, 11))));
        Assertions.assertTrue(sync(list.containsAll(Arrays.asList(30))));
    }

    @Test
    public void testContainsAllEmpty() {
        RListRx<Integer> list = redisson.getList("list");
        for (int i = 0; i < 200; i++) {
            sync(list.add(i));
        }

        Assertions.assertTrue(sync(list.containsAll(Collections.emptyList())));
        Assertions.assertTrue(Arrays.asList(1).containsAll(Collections.emptyList()));
    }

    @Test
    public void testIteratorSequence() {
        RListRx<String> list = redisson.getList("list2");
        sync(list.add("1"));
        sync(list.add("4"));
        sync(list.add("2"));
        sync(list.add("5"));
        sync(list.add("3"));

        checkIterator(list);
        // to test "memory effect" absence
        checkIterator(list);
    }

    private void checkIterator(RListRx<String> list) {
        int iteration = 0;
        for (Iterator<String> iterator = toIterator(list.iterator()); iterator.hasNext();) {
            String value = iterator.next();
            String val = sync(list.get(iteration));
            Assertions.assertEquals(val, value);
            iteration++;
        }

        Assertions.assertEquals(sync(list.size()).intValue(), iteration);
    }


    @Test
    public void testContains() {
        RListRx<String> list = redisson.getList("list");
        sync(list.add("1"));
        sync(list.add("4"));
        sync(list.add("2"));
        sync(list.add("5"));
        sync(list.add("3"));

        Assertions.assertTrue(sync(list.contains("3")));
        Assertions.assertFalse(sync(list.contains("31")));
        Assertions.assertTrue(sync(list.contains("1")));
    }

//    @Test(expected = RedisException.class)
//    public void testGetFail() {
//        RListRx<String> list = redisson.getList("list");
//
//        sync(list.get(0));
//    }

    @Test
    public void testAddGet() {
        RListRx<String> list = redisson.getList("list");
        sync(list.add("1"));
        sync(list.add("4"));
        sync(list.add("2"));
        sync(list.add("5"));
        sync(list.add("3"));

        String val1 = sync(list.get(0));
        Assertions.assertEquals("1", val1);

        String val2 = sync(list.get(3));
        Assertions.assertEquals("5", val2);
    }

    @Test
    public void testDuplicates() {
        RListRx<TestObject> list = redisson.getList("list");

        sync(list.add(new TestObject("1", "2")));
        sync(list.add(new TestObject("1", "2")));
        sync(list.add(new TestObject("2", "3")));
        sync(list.add(new TestObject("3", "4")));
        sync(list.add(new TestObject("5", "6")));

        Assertions.assertEquals(5, sync(list.size()).intValue());
    }

    @Test
    public void testSize() {
        RListRx<String> list = redisson.getList("list");

        sync(list.add("1"));
        sync(list.add("2"));
        sync(list.add("3"));
        sync(list.add("4"));
        sync(list.add("5"));
        sync(list.add("6"));
        assertThat(sync(list)).containsExactly("1", "2", "3", "4", "5", "6");

        sync(list.remove("2"));
        assertThat(sync(list)).containsExactly("1", "3", "4", "5", "6");

        sync(list.remove("4"));
        assertThat(sync(list)).containsExactly("1", "3", "5", "6");
    }

    @Test
    public void testCodec() {
        RListRx<Object> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2L));
        sync(list.add("3"));
        sync(list.add("e"));

        assertThat(sync(list)).containsExactly(1, 2L, "3", "e");
    }
}
