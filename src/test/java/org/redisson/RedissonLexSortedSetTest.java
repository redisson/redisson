package org.redisson;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RLexSortedSet;

public class RedissonLexSortedSetTest extends BaseTest {

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

        Assert.assertEquals(0, (int)set.removeRangeTailByLex("z", false));

        Assert.assertEquals(4, (int)set.removeRangeTailByLex("c", false));
        MatcherAssert.assertThat(set, Matchers.contains("a", "b", "c"));
        Assert.assertEquals(1, (int)set.removeRangeTailByLex("c", true));
        MatcherAssert.assertThat(set, Matchers.contains("a", "b"));
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

        Assert.assertEquals(2, (int)set.removeRangeHeadByLex("c", false));
        MatcherAssert.assertThat(set, Matchers.contains("c", "d", "e", "f", "g"));
        Assert.assertEquals(1, (int)set.removeRangeHeadByLex("c", true));
        MatcherAssert.assertThat(set, Matchers.contains("d", "e", "f", "g"));
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

        Assert.assertEquals(5, set.removeRangeByLex("aaa", true, "g", false));
        MatcherAssert.assertThat(set, Matchers.contains("a", "g"));
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

        MatcherAssert.assertThat(set.lexRangeTail("c", false), Matchers.contains("d", "e", "f", "g"));
        MatcherAssert.assertThat(set.lexRangeTail("c", true), Matchers.contains("c", "d", "e", "f", "g"));
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

        MatcherAssert.assertThat(set.lexRangeHead("c", false), Matchers.contains("a", "b"));
        MatcherAssert.assertThat(set.lexRangeHead("c", true), Matchers.contains("a", "b", "c"));
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

        MatcherAssert.assertThat(set.lexRange("aaa", true, "g", false), Matchers.contains("b", "c", "d", "e", "f"));
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

        Assert.assertEquals(5, (int)set.lexCount("b", true, "f", true));
        Assert.assertEquals(3, (int)set.lexCount("b", false, "f", false));
    }

}
