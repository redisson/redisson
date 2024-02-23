package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLexSortedSet;
import org.redisson.api.listener.ScoredSortedSetAddListener;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonLexSortedSetTest extends RedisDockerTest {

    @Test
    public void testRandom() {
        RLexSortedSet al = redisson.getLexSortedSet("test");
        for (int i = 0; i < 100; i++) {
            al.add("" + i);
        }

        Set<String> values = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            String v = al.random();
            values.add(v);
        }
        assertThat(values).hasSize(3);

        Collection<String> range = al.random(10);
        assertThat(range).hasSize(10);
    }

    @Test
    public void testAddListener() {
        testWithParams(redisson -> {
            RLexSortedSet al = redisson.getLexSortedSet("test");
            CountDownLatch latch = new CountDownLatch(1);
            al.addListener(new ScoredSortedSetAddListener() {
                @Override
                public void onAdd(String name) {
                    latch.countDown();
                }
            });
            al.add("abc");

            try {
                assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, NOTIFY_KEYSPACE_EVENTS, "Ez");
    }

    @Test
    public void testAll() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.addAll(Arrays.asList("foo", "bar"));
        assertThat(set.contains("foo")).isTrue();
        assertThat(set.contains("bar")).isTrue();
        assertThat(set.contains("123")).isFalse();
    }
    
    @Test
    public void testPollLast() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assertions.assertNull(set.pollLast());

        set.add("a");
        set.add("b");
        set.add("c");

        Assertions.assertEquals("c", set.pollLast());
        assertThat(set).containsExactly("a", "b");
    }

    @Test
    public void testPollFirst() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assertions.assertNull(set.pollFirst());

        set.add("a");
        set.add("b");
        set.add("c");

        Assertions.assertEquals("a", set.pollFirst());
        assertThat(set).containsExactly("b", "c");
    }

    @Test
    public void testFirstLast() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");

        Assertions.assertEquals("a", set.first());
        Assertions.assertEquals("d", set.last());
    }
    
    @Test
    public void testRemoveLexRangeTail() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assertions.assertTrue(set.add("a"));
        Assertions.assertFalse(set.add("a"));
        Assertions.assertTrue(set.add("b"));
        Assertions.assertTrue(set.add("c"));
        Assertions.assertTrue(set.add("d"));
        Assertions.assertTrue(set.add("e"));
        Assertions.assertTrue(set.add("f"));
        Assertions.assertTrue(set.add("g"));

        Assertions.assertEquals(0, (int)set.removeRangeTail("z", false));

        Assertions.assertEquals(4, (int)set.removeRangeTail("c", false));
        assertThat(set).containsExactly("a", "b", "c");
        Assertions.assertEquals(1, (int)set.removeRangeTail("c", true));
        assertThat(set).containsExactly("a", "b");
    }


    @Test
    public void testRemoveLexRangeHead() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        set.add("e");
        set.add("f");
        set.add("g");

        Assertions.assertEquals(2, (int)set.removeRangeHead("c", false));
        assertThat(set).containsExactly("c", "d", "e", "f", "g");
        Assertions.assertEquals(1, (int)set.removeRangeHead("c", true));
        assertThat(set).containsExactly("d", "e", "f", "g");
    }

    @Test
    public void testRemoveLexRange() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        set.add("e");
        set.add("f");
        set.add("g");

        Assertions.assertEquals(5, set.removeRange("aaa", true, "g", false));
        assertThat(set).containsExactly("a", "g");
    }


    @Test
    public void testLexRangeTail() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assertions.assertTrue(set.add("a"));
        Assertions.assertFalse(set.add("a"));
        Assertions.assertTrue(set.add("b"));
        Assertions.assertTrue(set.add("c"));
        Assertions.assertTrue(set.add("d"));
        Assertions.assertTrue(set.add("e"));
        Assertions.assertTrue(set.add("f"));
        Assertions.assertTrue(set.add("g"));

        assertThat(set.rangeTail("c", false)).containsExactly("d", "e", "f", "g");
        assertThat(set.rangeTail("c", true)).containsExactly("c", "d", "e", "f", "g");
        
        assertThat(set.rangeTail("c", false, 1, 2)).containsExactly("e", "f");
        assertThat(set.rangeTail("c", true, 1, 3)).containsExactly("d", "e", "f");
    }


    @Test
    public void testLexRangeHead() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        set.add("e");
        set.add("f");
        set.add("g");

        assertThat(set.rangeHead("c", false)).containsExactly("a", "b");
        assertThat(set.rangeHead("c", true)).containsExactly("a", "b", "c");
        
        assertThat(set.rangeHead("c", false, 1, 1)).containsExactly("b");
        assertThat(set.rangeHead("c", true, 1, 2)).containsExactly("b", "c");
    }


    @Test
    public void testLexRange() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        set.add("e");
        set.add("f");
        set.add("g");

        assertThat(set.range("aaa", true, "g", false)).containsExactly("b", "c", "d", "e", "f");
        assertThat(set.range("aaa", true, "g", false, 2, 3)).containsExactly("d", "e", "f");
    }

    @Test
    public void testLexRangeTailReversed() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assertions.assertTrue(set.add("a"));
        Assertions.assertFalse(set.add("a"));
        Assertions.assertTrue(set.add("b"));
        Assertions.assertTrue(set.add("c"));
        Assertions.assertTrue(set.add("d"));
        Assertions.assertTrue(set.add("e"));
        Assertions.assertTrue(set.add("f"));
        Assertions.assertTrue(set.add("g"));

        assertThat(set.rangeTailReversed("c", false)).containsExactly("g", "f", "e", "d");
        assertThat(set.rangeTailReversed("c", true)).containsExactly("g", "f", "e", "d", "c");
        
        assertThat(set.rangeTailReversed("c", false, 1, 2)).containsExactly("f", "e");
        assertThat(set.rangeTailReversed("c", true, 2, 2)).containsExactly("e", "d");
    }


    @Test
    public void testLexRangeHeadReversed() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        set.add("e");
        set.add("f");
        set.add("g");

        assertThat(set.rangeHeadReversed("c", false)).containsExactly("b", "a");
        assertThat(set.rangeHeadReversed("c", true)).containsExactly("c", "b", "a");
        
        assertThat(set.rangeHeadReversed("c", false, 1, 1)).containsExactly("a");
        assertThat(set.rangeHeadReversed("c", true, 1, 2)).containsExactly("b", "a");
    }


    @Test
    public void testLexRangeReversed() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        set.add("e");
        set.add("f");
        set.add("g");

        assertThat(set.rangeReversed("aaa", true, "g", false)).containsExactly("f", "e", "d", "c", "b");
        assertThat(set.rangeReversed("aaa", true, "g", false, 1, 2)).containsExactly("e", "d");
    }

    
    @Test
    public void testLexCount() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");
        set.add("e");
        set.add("f");
        set.add("g");

        assertThat(set.count("b", true, "f", true)).isEqualTo(5);
        assertThat(set.count("b", false, "f", false)).isEqualTo(3);
    }

}
