package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RLexSortedSet;

public class RedissonLexSortedSetTest extends BaseTest {

    @Test
    public void testPollLast() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assert.assertNull(set.pollLast());

        set.add("a");
        set.add("b");
        set.add("c");

        Assert.assertEquals("c", set.pollLast());
        MatcherAssert.assertThat(set, Matchers.contains("a", "b"));
    }

    @Test
    public void testPollFirst() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assert.assertNull(set.pollFirst());

        set.add("a");
        set.add("b");
        set.add("c");

        Assert.assertEquals("a", set.pollFirst());
        MatcherAssert.assertThat(set, Matchers.contains("b", "c"));
    }

    @Test
    public void testFirstLast() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("d");

        Assert.assertEquals("a", set.first());
        Assert.assertEquals("d", set.last());
    }
    
    @Test
    public void testRemoveLexRangeTail() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assert.assertTrue(set.add("a"));
        Assert.assertFalse(set.add("a"));
        Assert.assertTrue(set.add("b"));
        Assert.assertTrue(set.add("c"));
        Assert.assertTrue(set.add("d"));
        Assert.assertTrue(set.add("e"));
        Assert.assertTrue(set.add("f"));
        Assert.assertTrue(set.add("g"));

        Assert.assertEquals(0, (int)set.removeRangeTail("z", false));

        Assert.assertEquals(4, (int)set.removeRangeTail("c", false));
        assertThat(set).containsExactly("a", "b", "c");
        Assert.assertEquals(1, (int)set.removeRangeTail("c", true));
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

        Assert.assertEquals(2, (int)set.removeRangeHead("c", false));
        assertThat(set).containsExactly("c", "d", "e", "f", "g");
        Assert.assertEquals(1, (int)set.removeRangeHead("c", true));
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

        Assert.assertEquals(5, set.removeRange("aaa", true, "g", false));
        assertThat(set).containsExactly("a", "g");
    }


    @Test
    public void testLexRangeTail() {
        RLexSortedSet set = redisson.getLexSortedSet("simple");
        Assert.assertTrue(set.add("a"));
        Assert.assertFalse(set.add("a"));
        Assert.assertTrue(set.add("b"));
        Assert.assertTrue(set.add("c"));
        Assert.assertTrue(set.add("d"));
        Assert.assertTrue(set.add("e"));
        Assert.assertTrue(set.add("f"));
        Assert.assertTrue(set.add("g"));

        assertThat(set.rangeTail("c", false)).containsExactly("d", "e", "f", "g");
        assertThat(set.rangeTail("c", true)).containsExactly("c", "d", "e", "f", "g");
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
