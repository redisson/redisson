package org.redisson;

import java.util.BitSet;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RBitSet;

public class RedissonBitSetTest extends BaseTest {

    @Test
    public void testLength() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(0, 5);
        bs.clear(0, 1);
        Assert.assertEquals(5, bs.length());

        bs.clear();
        bs.set(28);
        bs.set(31);
        Assert.assertEquals(32, bs.length());

        bs.clear();
        bs.set(3);
        bs.set(7);
        Assert.assertEquals(8, bs.length());

        bs.clear();
        bs.set(3);
        bs.set(120);
        bs.set(121);
        Assert.assertEquals(122, bs.length());

        bs.clear();
        bs.set(0);
        Assert.assertEquals(1, bs.length());
    }

    @Test
    public void testClear() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(0, 8);
        bs.clear(0, 3);
        Assert.assertEquals("{3, 4, 5, 6, 7}", bs.toString());
    }

    @Test
    public void testNot() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(3);
        bs.set(5);
        bs.not();
        Assert.assertEquals("{0, 1, 2, 4, 6, 7}", bs.toString());
    }

    @Test
    public void testSet() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(3);
        bs.set(5);
        Assert.assertEquals("{3, 5}", bs.toString());

        BitSet bs1 = new BitSet();
        bs1.set(1);
        bs1.set(10);
        bs.set(bs1);

        bs = redisson.getBitSet("testbitset");

        Assert.assertEquals("{1, 10}", bs.toString());
    }

    @Test
    public void testSetGet() {
        RBitSet bitset = redisson.getBitSet("testbitset");
        Assert.assertEquals(0, bitset.cardinality());
        Assert.assertEquals(0, bitset.size());

        bitset.set(10, true);
        bitset.set(31, true);
        Assert.assertFalse(bitset.get(0));
        Assert.assertTrue(bitset.get(31));
        Assert.assertTrue(bitset.get(10));
        Assert.assertEquals(2, bitset.cardinality());
        Assert.assertEquals(32, bitset.size());
    }

    @Test
    public void testSetRange() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(3, 10);
        Assert.assertEquals(7, bs.cardinality());
        Assert.assertEquals(16, bs.size());
    }

    @Test
    public void testAsBitSet() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(3, true);
        bs.set(41, true);
        Assert.assertEquals(48, bs.size());

        BitSet bitset = bs.asBitSet();
        Assert.assertTrue(bitset.get(3));
        Assert.assertTrue(bitset.get(41));
        Assert.assertEquals(2, bitset.cardinality());
    }

    @Test
    public void testAnd() {
        RBitSet bs1 = redisson.getBitSet("testbitset1");
        bs1.set(3, 5);
        Assert.assertEquals(2, bs1.cardinality());
        Assert.assertEquals(8, bs1.size());

        RBitSet bs2 = redisson.getBitSet("testbitset2");
        bs2.set(4);
        bs2.set(10);
        bs1.and(bs2.getName());
        Assert.assertFalse(bs1.get(3));
        Assert.assertTrue(bs1.get(4));
        Assert.assertFalse(bs1.get(5));
        Assert.assertTrue(bs2.get(10));

        Assert.assertEquals(1, bs1.cardinality());
        Assert.assertEquals(16, bs1.size());
    }


}
