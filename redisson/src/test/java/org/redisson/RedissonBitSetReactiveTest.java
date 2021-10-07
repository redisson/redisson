package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBitSetReactive;

import java.util.BitSet;

public class RedissonBitSetReactiveTest extends BaseReactiveTest {

    @Test
    public void testLength() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(0, 5));
        sync(bs.clear(0, 1));
        Assertions.assertEquals(5, sync(bs.length()).intValue());

        sync(bs.clear());
        sync(bs.set(28));
        sync(bs.set(31));
        Assertions.assertEquals(32, sync(bs.length()).intValue());

        sync(bs.clear());
        sync(bs.set(3));
        sync(bs.set(7));
        Assertions.assertEquals(8, sync(bs.length()).intValue());

        sync(bs.clear());
        sync(bs.set(3));
        sync(bs.set(120));
        sync(bs.set(121));
        Assertions.assertEquals(122, sync(bs.length()).intValue());

        sync(bs.clear());
        sync(bs.set(0));
        Assertions.assertEquals(1, sync(bs.length()).intValue());
    }

    @Test
    public void testClear() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(0, 8));
        sync(bs.clear(0, 3));
        Assertions.assertEquals("{3, 4, 5, 6, 7}", bs.toString());
    }

    @Test
    public void testNot() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(3));
        sync(bs.set(5));
        sync(bs.not());
        Assertions.assertEquals("{0, 1, 2, 4, 6, 7}", bs.toString());
    }

    @Test
    public void testSet() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(3));
        sync(bs.set(5));
        Assertions.assertEquals("{3, 5}", bs.toString());

        BitSet bs1 = new BitSet();
        bs1.set(1);
        bs1.set(10);
        sync(bs.set(bs1));

        bs = redisson.getBitSet("testbitset");

        Assertions.assertEquals("{1, 10}", bs.toString());
    }

    @Test
    public void testSetGet() {
        RBitSetReactive bitset = redisson.getBitSet("testbitset");
        Assertions.assertEquals(0, sync(bitset.cardinality()).intValue());
        Assertions.assertEquals(0, sync(bitset.size()).intValue());

        sync(bitset.set(10, true));
        sync(bitset.set(31, true));
        Assertions.assertFalse(sync(bitset.get(0)));
        Assertions.assertTrue(sync(bitset.get(31)));
        Assertions.assertTrue(sync(bitset.get(10)));
        Assertions.assertEquals(2, sync(bitset.cardinality()).intValue());
        Assertions.assertEquals(32, sync(bitset.size()).intValue());
    }

    @Test
    public void testSetRange() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(3, 10));
        Assertions.assertEquals(7, sync(bs.cardinality()).intValue());
        Assertions.assertEquals(16, sync(bs.size()).intValue());
    }

    @Test
    public void testAnd() {
        RBitSetReactive bs1 = redisson.getBitSet("testbitset1");
        sync(bs1.set(3, 5));
        Assertions.assertEquals(2, sync(bs1.cardinality()).intValue());
        Assertions.assertEquals(8, sync(bs1.size()).intValue());

        RBitSetReactive bs2 = redisson.getBitSet("testbitset2");
        sync(bs2.set(4));
        sync(bs2.set(10));
        sync(bs1.and(bs2.getName()));
        Assertions.assertFalse(sync(bs1.get(3)));
        Assertions.assertTrue(sync(bs1.get(4)));
        Assertions.assertFalse(sync(bs1.get(5)));
        Assertions.assertTrue(sync(bs2.get(10)));

        Assertions.assertEquals(1, sync(bs1.cardinality()).intValue());
        Assertions.assertEquals(16, sync(bs1.size()).intValue());
    }


}
