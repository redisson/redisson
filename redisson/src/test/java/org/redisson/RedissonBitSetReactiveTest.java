package org.redisson;

import java.util.BitSet;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBitSetReactive;

public class RedissonBitSetReactiveTest extends BaseReactiveTest {

    @Test
    public void testLength() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(0, 5));
        sync(bs.clear(0, 1));
        Assert.assertEquals(5, sync(bs.length()).intValue());

        sync(bs.clear());
        sync(bs.set(28));
        sync(bs.set(31));
        Assert.assertEquals(32, sync(bs.length()).intValue());

        sync(bs.clear());
        sync(bs.set(3));
        sync(bs.set(7));
        Assert.assertEquals(8, sync(bs.length()).intValue());

        sync(bs.clear());
        sync(bs.set(3));
        sync(bs.set(120));
        sync(bs.set(121));
        Assert.assertEquals(122, sync(bs.length()).intValue());

        sync(bs.clear());
        sync(bs.set(0));
        Assert.assertEquals(1, sync(bs.length()).intValue());
    }

    @Test
    public void testClear() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(0, 8));
        sync(bs.clear(0, 3));
        Assert.assertEquals("{3, 4, 5, 6, 7}", bs.toString());
    }

    @Test
    public void testNot() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(3));
        sync(bs.set(5));
        sync(bs.not());
        Assert.assertEquals("{0, 1, 2, 4, 6, 7}", bs.toString());
    }

    @Test
    public void testSet() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(3));
        sync(bs.set(5));
        Assert.assertEquals("{3, 5}", bs.toString());

        BitSet bs1 = new BitSet();
        bs1.set(1);
        bs1.set(10);
        sync(bs.set(bs1));

        bs = redisson.getBitSet("testbitset");

        Assert.assertEquals("{1, 10}", bs.toString());
    }

    @Test
    public void testSetGet() {
        RBitSetReactive bitset = redisson.getBitSet("testbitset");
        Assert.assertEquals(0, sync(bitset.cardinality()).intValue());
        Assert.assertEquals(0, sync(bitset.size()).intValue());

        sync(bitset.set(10, true));
        sync(bitset.set(31, true));
        Assert.assertFalse(sync(bitset.get(0)));
        Assert.assertTrue(sync(bitset.get(31)));
        Assert.assertTrue(sync(bitset.get(10)));
        Assert.assertEquals(2, sync(bitset.cardinality()).intValue());
        Assert.assertEquals(32, sync(bitset.size()).intValue());
    }

    @Test
    public void testSetRange() {
        RBitSetReactive bs = redisson.getBitSet("testbitset");
        sync(bs.set(3, 10));
        Assert.assertEquals(7, sync(bs.cardinality()).intValue());
        Assert.assertEquals(16, sync(bs.size()).intValue());
    }

    @Test
    public void testAnd() {
        RBitSetReactive bs1 = redisson.getBitSet("testbitset1");
        sync(bs1.set(3, 5));
        Assert.assertEquals(2, sync(bs1.cardinality()).intValue());
        Assert.assertEquals(8, sync(bs1.size()).intValue());

        RBitSetReactive bs2 = redisson.getBitSet("testbitset2");
        sync(bs2.set(4));
        sync(bs2.set(10));
        sync(bs1.and(bs2.getName()));
        Assert.assertFalse(sync(bs1.get(3)));
        Assert.assertTrue(sync(bs1.get(4)));
        Assert.assertFalse(sync(bs1.get(5)));
        Assert.assertTrue(sync(bs2.get(10)));

        Assert.assertEquals(1, sync(bs1.cardinality()).intValue());
        Assert.assertEquals(16, sync(bs1.size()).intValue());
    }


}
