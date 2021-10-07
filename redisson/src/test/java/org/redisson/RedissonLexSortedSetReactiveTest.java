package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RCollectionReactive;
import org.redisson.api.RLexSortedSetReactive;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonLexSortedSetReactiveTest extends BaseReactiveTest {

    @Test
    public void testAddAllReactive() {
        RLexSortedSetReactive list = redisson.getLexSortedSet("set");
        Assertions.assertTrue(sync(list.add("1")));
        Assertions.assertTrue(sync(list.add("2")));
        Assertions.assertTrue(sync(list.add("3")));
        Assertions.assertTrue(sync(list.add("4")));
        Assertions.assertTrue(sync(list.add("5")));

        RLexSortedSetReactive list2 = redisson.getLexSortedSet("set2");
        Assertions.assertEquals(true, sync(list2.addAll(list.iterator())));
        Assertions.assertEquals(5, sync(list2.size()).intValue());
    }

    @Test
    public void testRemoveLexRangeTail() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        Assertions.assertTrue(sync(set.add("a")));
        Assertions.assertFalse(sync(set.add("a")));
        Assertions.assertTrue(sync(set.add("b")));
        Assertions.assertTrue(sync(set.add("c")));
        Assertions.assertTrue(sync(set.add("d")));
        Assertions.assertTrue(sync(set.add("e")));
        Assertions.assertTrue(sync(set.add("f")));
        Assertions.assertTrue(sync(set.add("g")));

        Assertions.assertEquals(0, sync(set.removeRangeTail("z", false)).intValue());

        Assertions.assertEquals(4, sync(set.removeRangeTail("c", false)).intValue());
        assertThat(sync((RCollectionReactive)set)).containsExactly("a", "b", "c");
        Assertions.assertEquals(1, sync(set.removeRangeTail("c", true)).intValue());
        assertThat(sync((RCollectionReactive)set)).containsExactly("a", "b");
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

        Assertions.assertEquals(2, sync(set.removeRangeHead("c", false)).intValue());
        assertThat(sync((RCollectionReactive)set)).containsExactly("c", "d", "e", "f", "g");
        Assertions.assertEquals(1, (int)sync(set.removeRangeHead("c", true)));
        assertThat(sync((RCollectionReactive)set)).containsExactly("d", "e", "f", "g");
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

        Assertions.assertEquals(5, sync(set.removeRange("aaa", true, "g", false)).intValue());
        assertThat(sync((RCollectionReactive)set)).containsExactly("a", "g");
    }


    @Test
    public void testLexRangeTail() {
        RLexSortedSetReactive set = redisson.getLexSortedSet("simple");
        Assertions.assertTrue(sync(set.add("a")));
        Assertions.assertFalse(sync(set.add("a")));
        Assertions.assertTrue(sync(set.add("b")));
        Assertions.assertTrue(sync(set.add("c")));
        Assertions.assertTrue(sync(set.add("d")));
        Assertions.assertTrue(sync(set.add("e")));
        Assertions.assertTrue(sync(set.add("f")));
        Assertions.assertTrue(sync(set.add("g")));

        assertThat(sync(set.rangeTail("c", false))).containsExactly("d", "e", "f", "g");
        assertThat(sync(set.rangeTail("c", true))).containsExactly("c", "d", "e", "f", "g");
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

        assertThat(sync(set.rangeHead("c", false))).containsExactly("a", "b");
        assertThat(sync(set.rangeHead("c", true))).containsExactly("a", "b", "c");
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

        assertThat(sync(set.range("aaa", true, "g", false))).contains("b", "c", "d", "e", "f");
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

        Assertions.assertEquals(5, (int)sync(set.count("b", true, "f", true)));
        Assertions.assertEquals(3, (int)sync(set.count("b", false, "f", false)));
    }

}
