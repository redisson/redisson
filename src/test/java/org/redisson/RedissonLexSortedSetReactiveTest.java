package org.redisson;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RLexSortedSetReactive;

public class RedissonLexSortedSetReactiveTest extends BaseReactiveTest {

    @Test
    public void testAddAllReactive() {
        RLexSortedSetReactive list = redisson.getLexSortedSet("set");
        Assert.assertTrue(sync(list.add("1")) == 1);
        Assert.assertTrue(sync(list.add("2"))  == 1);
        Assert.assertTrue(sync(list.add("3")) == 1);
        Assert.assertTrue(sync(list.add("4")) == 1);
        Assert.assertTrue(sync(list.add("5")) == 1);

        RLexSortedSetReactive list2 = redisson.getLexSortedSet("set2");
        Assert.assertEquals(5, sync(list2.addAll(list.iterator())).intValue());
        Assert.assertEquals(5, sync(list2.size()).intValue());
    }

    @Test
    public void testRemoveLexRangeTail() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        Assert.assertTrue(sync(set.add("a")) == 1);
        Assert.assertFalse(sync(set.add("a")) == 1);
        Assert.assertTrue(sync(set.add("b"))  == 1);
        Assert.assertTrue(sync(set.add("c")) == 1);
        Assert.assertTrue(sync(set.add("d")) == 1);
        Assert.assertTrue(sync(set.add("e")) == 1);
        Assert.assertTrue(sync(set.add("f")) == 1);
        Assert.assertTrue(sync(set.add("g")) == 1);

        Assert.assertEquals(0, sync(set.removeRangeTailByLex("z", false)).intValue());

        Assert.assertEquals(4, sync(set.removeRangeTailByLex("c", false)).intValue());
        MatcherAssert.assertThat(sync(set), Matchers.contains("a", "b", "c"));
        Assert.assertEquals(1, sync(set.removeRangeTailByLex("c", true)).intValue());
        MatcherAssert.assertThat(sync(set), Matchers.contains("a", "b"));
    }


    @Test
    public void testRemoveLexRangeHead() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        Assert.assertEquals(2, sync(set.removeRangeHeadByLex("c", false)).intValue());
        MatcherAssert.assertThat(sync(set), Matchers.contains("c", "d", "e", "f", "g"));
        Assert.assertEquals(1, (int)sync(set.removeRangeHeadByLex("c", true)));
        MatcherAssert.assertThat(sync(set), Matchers.contains("d", "e", "f", "g"));
    }

    @Test
    public void testRemoveLexRange() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        Assert.assertEquals(5, sync(set.removeRangeByLex("aaa", true, "g", false)).intValue());
        MatcherAssert.assertThat(sync(set), Matchers.contains("a", "g"));
    }


    @Test
    public void testLexRangeTail() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        Assert.assertTrue(sync(set.add("a")) == 1);
        Assert.assertFalse(sync(set.add("a")) == 1);
        Assert.assertTrue(sync(set.add("b")) == 1);
        Assert.assertTrue(sync(set.add("c")) == 1);
        Assert.assertTrue(sync(set.add("d")) == 1);
        Assert.assertTrue(sync(set.add("e")) == 1);
        Assert.assertTrue(sync(set.add("f")) == 1);
        Assert.assertTrue(sync(set.add("g")) == 1);

        MatcherAssert.assertThat(sync(set.lexRangeTail("c", false)), Matchers.contains("d", "e", "f", "g"));
        MatcherAssert.assertThat(sync(set.lexRangeTail("c", true)), Matchers.contains("c", "d", "e", "f", "g"));
    }


    @Test
    public void testLexRangeHead() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        MatcherAssert.assertThat(sync(set.lexRangeHead("c", false)), Matchers.contains("a", "b"));
        MatcherAssert.assertThat(sync(set.lexRangeHead("c", true)), Matchers.contains("a", "b", "c"));
    }


    @Test
    public void testLexRange() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        MatcherAssert.assertThat(sync(set.lexRange("aaa", true, "g", false)), Matchers.contains("b", "c", "d", "e", "f"));
    }

    @Test
    public void testLexCount() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        Assert.assertEquals(5, (int)sync(set.lexCount("b", true, "f", true)));
        Assert.assertEquals(3, (int)sync(set.lexCount("b", false, "f", false)));
    }

}
