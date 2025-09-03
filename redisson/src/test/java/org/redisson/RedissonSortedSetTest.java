package org.redisson;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.NameMapper;
import org.redisson.api.RFuture;
import org.redisson.api.RSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;

public class RedissonSortedSetTest extends RedisDockerTest {

    @Test
    public void testNameMapper() {
        testInCluster(client -> {
            Config config = client.getConfig();
            config.useClusterServers()
                    .setNameMapper(new NameMapper() {
                        @Override
                        public String map(String name) {
                            return name + ":suffix:";
                        }

                        @Override
                        public String unmap(String name) {
                            return name.replace(":suffix:", "");
                        }
                    });
            RedissonClient redisson = Redisson.create(config);

            RSortedSet<Long> set = redisson.getSortedSet("simple", LongCodec.INSTANCE);
            set.add(2L);
            set.add(0L);
            set.add(1L);
            set.add(5L);

            assertThat(set.first()).isEqualTo(0L);
            assertThat(set.last()).isEqualTo(5L);

            assertThat(set.readAll()).containsExactly(0L, 1L, 2L, 5L);
            set.delete();

            assertThat(redisson.getKeys().count()).isZero();
            redisson.shutdown();
        });
    }


    @Test
    public void test1() {
        RSortedSet<Long> set = redisson.getSortedSet("simple", LongCodec.INSTANCE);
        set.add(2L);
        set.add(0L);
        set.add(1L);
        set.add(5L);
        
        assertThat(set.readAll()).containsExactly(0L, 1L, 2L, 5L);
    }
    
    @Test
    public void testReadAll() {
        RSortedSet<Integer> set = redisson.getSortedSet("simple");
        set.add(2);
        set.add(0);
        set.add(1);
        set.add(5);
        
        assertThat(set.readAll()).containsExactly(0, 1, 2, 5);
    }
    
    @Test
    public void testAddAsync() throws InterruptedException, ExecutionException {
        RSortedSet<Integer> set = redisson.getSortedSet("simple");
        RFuture<Boolean> future = set.addAsync(2);
        Assertions.assertTrue(future.get());

        Assertions.assertTrue(set.contains(2));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RSortedSet<Integer> set = redisson.getSortedSet("simple");
        set.add(1);
        set.add(3);
        set.add(7);

        Assertions.assertTrue(set.removeAsync(1).get());
        Assertions.assertFalse(set.contains(1));
        assertThat(set).containsExactly(3, 7);

        Assertions.assertFalse(set.removeAsync(1).get());
        assertThat(set).containsExactly(3, 7);
        
        set.removeAsync(3).get();
        Assertions.assertFalse(set.contains(3));
        assertThat(set).containsExactly(7);
    }
    
    @Test
    public void testIteratorNextNext() {
        RSortedSet<String> list = redisson.getSortedSet("simple");
        list.add("1");
        list.add("4");

        Iterator<String> iter = list.iterator();
        Assertions.assertEquals("1", iter.next());
        Assertions.assertEquals("4", iter.next());
        Assertions.assertFalse(iter.hasNext());
    }
    
    @Test
    public void testIteratorRemove() {
        RSortedSet<String> list = redisson.getSortedSet("list");
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
        Set<Integer> set = redisson.getSortedSet("set");
        for (int i = 0; i < 1000; i++) {
            set.add(Integer.valueOf(i));
        }

        Set<Integer> setCopy = new HashSet<Integer>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Integer.valueOf(i));
        }
        
        checkIterator(set, setCopy);
    }

    private void checkIterator(Set<Integer> set, Set<Integer> setCopy) {
        for (Iterator<Integer> iterator = set.iterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!setCopy.remove(value)) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, setCopy.size());
    }
    
    @Test
    public void testTrySetComparator() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");

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
    public void testOrder2() {
        TreeSet<Integer> set = new TreeSet<Integer>();

        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        SortedSet<Integer> hs = set.headSet(6);
    }

//    @Test(expected = IllegalArgumentException.class)
    public void testTailSet() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");

        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        SortedSet<Integer> hs = set.tailSet(3);
        hs.add(10);

        assertThat(hs).containsExactly(3, 4, 5, 10);

        set.remove(4);

        assertThat(hs).containsExactly(3, 5, 10);

        set.remove(3);

        assertThat(hs).containsExactly(5, 10);

        hs.add(-1);
    }


//    @Test(expected = IllegalArgumentException.class)
    public void testHeadSet() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");

        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        SortedSet<Integer> hs = set.headSet(3);
        hs.add(0);

        assertThat(hs).containsExactly(0, 1, 2);

        set.remove(2);

        assertThat(hs).containsExactly(0, 1);

        set.remove(3);

        assertThat(hs).containsExactly(0, 1);

        hs.add(7);
    }

    @Test
    public void testTailSetTreeSet() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            TreeSet<Integer> set = new TreeSet<Integer>();

            set.add(1);
            set.add(2);
            set.add(3);
            set.add(4);
            set.add(5);

            SortedSet<Integer> hs = set.tailSet(3);
            hs.add(10);

            assertThat(hs).containsExactly(3, 4, 5, 10);

            set.remove(4);

            assertThat(hs).containsExactly(3, 5, 10);

            set.remove(3);

            assertThat(hs).containsExactly(5, 10);

            hs.add(-1);
        });
    }

    @Test
    public void testHeadSetTreeSet() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            TreeSet<Integer> set = new TreeSet<>();

            set.add(1);
            set.add(2);
            set.add(3);
            set.add(4);
            set.add(5);

            SortedSet<Integer> hs = set.headSet(3);
            hs.add(0);

            assertThat(hs).containsExactly(0, 1, 2);

            set.remove(2);

            assertThat(hs).containsExactly(0, 1);

            set.remove(3);

            assertThat(hs).containsExactly(0, 1);

            hs.add(7);
        });
    }

    @Test
    public void testSort() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");
        Assertions.assertTrue(set.add(2));
        Assertions.assertTrue(set.add(3));
        Assertions.assertTrue(set.add(1));
        Assertions.assertTrue(set.add(4));
        Assertions.assertTrue(set.add(10));
        Assertions.assertTrue(set.add(-1));
        Assertions.assertTrue(set.add(0));

        assertThat(set).containsExactly(-1, 0, 1, 2, 3, 4, 10);

        Assertions.assertEquals(-1, (int)set.first());
        Assertions.assertEquals(10, (int)set.last());
    }

    @Test
    public void testRemove() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");
        set.add(5);
        set.add(3);
        set.add(1);
        set.add(2);
        set.add(4);

        Assertions.assertFalse(set.remove(0));
        Assertions.assertTrue(set.remove(3));

        assertThat(set).containsExactly(1, 2, 4, 5);
    }

    @Test
    public void testRetainAll() {
        Set<Integer> set = redisson.getSortedSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assertions.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        Assertions.assertEquals(2, set.size());
    }

    @Test
    public void testContainsAll() {
        Set<Integer> set = redisson.getSortedSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assertions.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assertions.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
    }

    @Test
    public void testToArray() {
        Set<String> set = redisson.getSortedSet("set");
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
        Set<TestObject> set = redisson.getSortedSet("set");

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
        Set<TestObject> set = redisson.getSortedSet("set");

        Assertions.assertTrue(set.add(new TestObject("1", "2")));
        Assertions.assertFalse(set.add(new TestObject("1", "2")));
        Assertions.assertTrue(set.add(new TestObject("2", "3")));
        Assertions.assertTrue(set.add(new TestObject("3", "4")));
        Assertions.assertTrue(set.add(new TestObject("5", "6")));

        Assertions.assertEquals(4, set.size());
    }

    @Test
    public void testSize() {
        Set<Integer> set = redisson.getSortedSet("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(5);

        Assertions.assertEquals(5, set.size());
    }

    @Test
    public void testDistributedIterator() {
        RSortedSet<String> set = redisson.getSortedSet("set", StringCodec.INSTANCE);

        // populate set with elements
        List<String> strings = IntStream.range(0, 128).mapToObj(i -> "one-" + i).collect(Collectors.toList());
        set.addAll(strings);

        Iterator<String> stringIterator = set.distributedIterator("iterator_{set}", 10);

        // read some elements using iterator
        List<String> result = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (stringIterator.hasNext()) {
                result.add(stringIterator.next());
            }
        }

        // create another iterator instance using the same name
        RSortedSet<String> set2 = redisson.getSortedSet("set", StringCodec.INSTANCE);
        Iterator<String> stringIterator2 = set2.distributedIterator("iterator_{set}", 10);

        assertTrue(stringIterator2.hasNext());

        // read all remaining elements
        stringIterator2.forEachRemaining(result::add);
        stringIterator.forEachRemaining(result::add);

        assertThat(result).containsAll(strings);
        assertThat(result).hasSize(strings.size());
    }

    @Test
    public void testPollFirst() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");
        set.add(3);
        set.add(1);
        set.add(2);
        assertThat(set.pollFirst()).isEqualTo(1);

        assertThat(set.pollFirst(2)).containsExactly(2,3);

        long s = System.currentTimeMillis();
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            set.add(4);
        }, 3, TimeUnit.SECONDS);

        assertThat(set.pollFirst(Duration.ofSeconds(1))).isNull();

        List<Integer> list=set.pollFirst(Duration.ofSeconds(4),1);
        assertThat(list).containsExactly(4);
        assertThat(System.currentTimeMillis()-s).isLessThan(4000);

    }

    @Test
    public void testPollLast() {
        RSortedSet<Integer> set = redisson.getSortedSet("set");
        set.add(3);
        set.add(1);
        set.add(2);
        assertThat(set.pollLast()).isEqualTo(3);

        assertThat(set.pollLast(2)).containsExactly(2, 1);

        long s = System.currentTimeMillis();
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            set.add(4);
        }, 3, TimeUnit.SECONDS);

        assertThat(set.pollLast(Duration.ofSeconds(1))).isNull();

        List<Integer> list = set.pollLast(Duration.ofSeconds(4), 1);
        assertThat(list).containsExactly(4);
        assertThat(System.currentTimeMillis() - s).isLessThan(4000);

        set.add(3);
        set.add(1);
        set.add(2);

        assertThat(set.pollLast(Duration.ofSeconds(1), 2)).containsExactly(3, 2);

    }
}
