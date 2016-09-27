package org.redisson;

import static org.assertj.core.api.Assertions.*;
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
        assertThat(sync(set)).containsExactly("a", "b", "c");
        Assert.assertEquals(1, sync(set.removeRangeTailByLex("c", true)).intValue());
        assertThat(sync(set)).containsExactly("a", "b");
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
        assertThat(sync(set)).containsExactly("c", "d", "e", "f", "g");
        Assert.assertEquals(1, (int)sync(set.removeRangeHeadByLex("c", true)));
        assertThat(sync(set)).containsExactly("d", "e", "f", "g");
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
        assertThat(sync(set)).containsExactly("a", "g");
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

        assertThat(sync(set.lexRangeTail("c", false))).containsExactly("d", "e", "f", "g");
        assertThat(sync(set.lexRangeTail("c", true))).containsExactly("c", "d", "e", "f", "g");
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

        assertThat(sync(set.lexRangeHead("c", false))).containsExactly("a", "b");
        assertThat(sync(set.lexRangeHead("c", true))).containsExactly("a", "b", "c");
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

        assertThat(sync(set.lexRange("aaa", true, "g", false))).contains("b", "c", "d", "e", "f");
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
