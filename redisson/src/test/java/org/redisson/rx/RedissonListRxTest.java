package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.TestObject;
import org.redisson.api.RListRx;
import org.redisson.client.RedisException;

import reactor.core.publisher.BaseSubscriber;

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
        Assert.assertEquals(true, sync(list2.addAll(list.iterator())));
        Assert.assertEquals(5, sync(list2.size()).intValue());
    }

    @Test
    public void testAddAllWithIndex() throws InterruptedException {
        final RListRx<Long> list = redisson.getList("list");
        final CountDownLatch latch = new CountDownLatch(1);
        list.addAll(Arrays.asList(1L, 2L, 3L)).subscribe(new BaseSubscriber<Boolean>() {

            @Override
            public void hookOnNext(Boolean element) {
                list.addAll(Arrays.asList(1L, 24L, 3L)).subscribe(new BaseSubscriber<Boolean>() {
                    @Override
                    public void hookOnNext(Boolean value) {
                        latch.countDown();
                    }

                    @Override
                    public void hookOnError(Throwable error) {
                        Assert.fail(error.getMessage());
                    }
                });
            }

            @Override
            public void hookOnError(Throwable error) {
                Assert.fail(error.getMessage());
            }
        });

        latch.await();

        assertThat(sync(list)).containsExactly(1L, 2L, 3L, 1L, 24L, 3L);
    }

    @Test
    public void testAdd() throws InterruptedException {
        final RListRx<Long> list = redisson.getList("list");
        final CountDownLatch latch = new CountDownLatch(1);
        list.add(1L).subscribe(new BaseSubscriber<Boolean>() {
            @Override
            public void hookOnNext(Boolean value) {
                list.add(2L).subscribe(new BaseSubscriber<Boolean>() {
                    @Override
                    public void hookOnNext(Boolean value) {
                        latch.countDown();
                    }

                    @Override
                    public void hookOnError(Throwable error) {
                        Assert.fail(error.getMessage());
                    }
                });
            }

            @Override
            public void hookOnError(Throwable error) {
                Assert.fail(error.getMessage());
            }
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

        Assert.assertTrue(1 == iterator.next());
        Assert.assertTrue(2 == iterator.next());
        Assert.assertTrue(3 == iterator.next());
        Assert.assertTrue(4 == iterator.next());
        Assert.assertTrue(5 == iterator.next());
        Assert.assertTrue(0 == iterator.next());
        Assert.assertTrue(7 == iterator.next());
        Assert.assertTrue(8 == iterator.next());
        Assert.assertTrue(0 == iterator.next());
        Assert.assertTrue(10 == iterator.next());
        Assert.assertFalse(iterator.hasNext());
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

        Assert.assertTrue(10 == iterator.next());
        Assert.assertTrue(0 == iterator.next());
        Assert.assertTrue(8 == iterator.next());
        Assert.assertTrue(7 == iterator.next());
        Assert.assertTrue(0 == iterator.next());
        Assert.assertTrue(5 == iterator.next());
        Assert.assertTrue(4 == iterator.next());
        Assert.assertTrue(3 == iterator.next());
        Assert.assertTrue(2 == iterator.next());
        Assert.assertTrue(1 == iterator.next());
        Assert.assertFalse(iterator.hasNext());
    }

    @Test
    public void testLastIndexOfNone() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assert.assertEquals(-1, sync(list.lastIndexOf(10)).intValue());
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
        Assert.assertEquals(2, index);
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
        Assert.assertEquals(5, index);
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
        Assert.assertEquals(8, index);
    }

    @Test
    public void testIndexOf() {
        RListRx<Integer> list = redisson.getList("list");
        for (int i = 1; i < 200; i++) {
            sync(list.add(i));
        }

        Assert.assertTrue(55 == sync(list.indexOf(56)));
        Assert.assertTrue(99 == sync(list.indexOf(100)));
        Assert.assertTrue(-1 == sync(list.indexOf(200)));
        Assert.assertTrue(-1 == sync(list.indexOf(0)));
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
        Assert.assertTrue(1 == val);

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

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetFail() throws InterruptedException {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        sync(list.set(5, 6));
    }

    @Test
    public void testRemoveAllEmpty() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assert.assertFalse(sync(list.removeAll(Collections.emptyList())));
        Assert.assertFalse(Arrays.asList(1).removeAll(Collections.emptyList()));
    }

    @Test
    public void testRemoveAll() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assert.assertFalse(sync(list.removeAll(Collections.emptyList())));
        Assert.assertTrue(sync(list.removeAll(Arrays.asList(3, 2, 10, 6))));

        assertThat(sync(list)).containsExactly(1, 4, 5);

        Assert.assertTrue(sync(list.removeAll(Arrays.asList(4))));

        assertThat(sync(list)).containsExactly(1, 5);

        Assert.assertTrue(sync(list.removeAll(Arrays.asList(1, 5, 1, 5))));

        Assert.assertEquals(0, sync(list.size()).longValue());
    }

    @Test
    public void testRetainAll() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assert.assertTrue(sync(list.retainAll(Arrays.asList(3, 2, 10, 6))));

        assertThat(sync(list)).containsExactly(2, 3);
        Assert.assertEquals(2, sync(list.size()).longValue());
    }

    @Test
    public void testFastSet() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));

        sync(list.fastSet(0, 3));
        Assert.assertEquals(3, (int)sync(list.get(0)));
    }

    @Test
    public void testRetainAllEmpty() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assert.assertTrue(sync(list.retainAll(Collections.<Integer>emptyList())));
        Assert.assertEquals(0, sync(list.size()).intValue());
    }

    @Test
    public void testRetainAllNoModify() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));

        Assert.assertFalse(sync(list.retainAll(Arrays.asList(1, 2)))); // nothing changed
        assertThat(sync(list)).containsExactly(1, 2);
    }

    @Test(expected = RedisException.class)
    public void testAddAllIndexError() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.addAll(2, Arrays.asList(7, 8, 9)));
    }

    @Test
    public void testAddAllIndex() {
        RListRx<Integer> list = redisson.getList("list");
        sync(list.add(1));
        sync(list.add(2));
        sync(list.add(3));
        sync(list.add(4));
        sync(list.add(5));

        Assert.assertEquals(true, sync(list.addAll(2, Arrays.asList(7, 8, 9))));

        assertThat(sync(list)).containsExactly(1, 2, 7, 8, 9, 3, 4, 5);

        sync(list.addAll(sync(list.size())-1, Arrays.asList(9, 1, 9)));

        assertThat(sync(list)).containsExactly(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5);

        sync(list.addAll(sync(list.size()), Arrays.asList(0, 5)));

        assertThat(sync(list)).containsExactly(1, 2, 7, 8, 9, 3, 4, 9, 1, 9, 5, 0, 5);

        Assert.assertEquals(true, sync(list.addAll(0, Arrays.asList(6, 7))));

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

        Assert.assertEquals(true, sync(list.addAll(Arrays.asList(7, 8, 9))));

        Assert.assertEquals(true, sync(list.addAll(Arrays.asList(9, 1, 9))));

        assertThat(sync(list)).containsExactly(1, 2, 3, 4, 5, 7, 8, 9, 9, 1, 9);
    }

    @Test
    public void testAddAllEmpty() {
        RListRx<Integer> list = redisson.getList("list");
        Assert.assertEquals(false, sync(list.addAll(Collections.<Integer>emptyList())));
        Assert.assertEquals(0, sync(list.size()).intValue());
    }

    @Test
    public void testContainsAll() {
        RListRx<Integer> list = redisson.getList("list");
        for (int i = 0; i < 200; i++) {
            sync(list.add(i));
        }

        Assert.assertTrue(sync(list.containsAll(Arrays.asList(30, 11))));
        Assert.assertFalse(sync(list.containsAll(Arrays.asList(30, 711, 11))));
        Assert.assertTrue(sync(list.containsAll(Arrays.asList(30))));
    }

    @Test
    public void testContainsAllEmpty() {
        RListRx<Integer> list = redisson.getList("list");
        for (int i = 0; i < 200; i++) {
            sync(list.add(i));
        }

        Assert.assertTrue(sync(list.containsAll(Collections.emptyList())));
        Assert.assertTrue(Arrays.asList(1).containsAll(Collections.emptyList()));
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
            Assert.assertEquals(val, value);
            iteration++;
        }

        Assert.assertEquals(sync(list.size()).intValue(), iteration);
    }


    @Test
    public void testContains() {
        RListRx<String> list = redisson.getList("list");
        sync(list.add("1"));
        sync(list.add("4"));
        sync(list.add("2"));
        sync(list.add("5"));
        sync(list.add("3"));

        Assert.assertTrue(sync(list.contains("3")));
        Assert.assertFalse(sync(list.contains("31")));
        Assert.assertTrue(sync(list.contains("1")));
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
        Assert.assertEquals("1", val1);

        String val2 = sync(list.get(3));
        Assert.assertEquals("5", val2);
    }

    @Test
    public void testDuplicates() {
        RListRx<TestObject> list = redisson.getList("list");

        sync(list.add(new TestObject("1", "2")));
        sync(list.add(new TestObject("1", "2")));
        sync(list.add(new TestObject("2", "3")));
        sync(list.add(new TestObject("3", "4")));
        sync(list.add(new TestObject("5", "6")));

        Assert.assertEquals(5, sync(list.size()).intValue());
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
