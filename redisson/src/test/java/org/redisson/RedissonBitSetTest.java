package org.redisson;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.*;
import org.junit.Test;
import org.redisson.api.RBitSet;

public class RedissonBitSetTest extends BaseTest {

    @Test
    public void testIndexRange() {
        RBitSet bs = redisson.getBitSet("testbitset");
        long topIndex = Integer.MAX_VALUE*2L;
        assertThat(bs.get(topIndex)).isFalse();
        bs.set(topIndex);
        assertThat(bs.get(topIndex)).isTrue();
    }

    @Test
    public void testLength() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(0, 5);
        bs.clear(0, 1);
        assertThat(bs.length()).isEqualTo(5);

        bs.clear();
        bs.set(28);
        bs.set(31);
        assertThat(bs.length()).isEqualTo(32);

        bs.clear();
        bs.set(3);
        bs.set(7);
        assertThat(bs.length()).isEqualTo(8);

        bs.clear();
        bs.set(3);
        bs.set(120);
        bs.set(121);
        assertThat(bs.length()).isEqualTo(122);

        bs.clear();
        bs.set(0);
        assertThat(bs.length()).isEqualTo(1);
    }

    @Test
    public void testClear() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(0, 8);
        bs.clear(0, 3);
        assertThat(bs.toString()).isEqualTo("{3, 4, 5, 6, 7}");
    }

    @Test
    public void testNot() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(3);
        bs.set(5);
        bs.not();
        assertThat(bs.toString()).isEqualTo("{0, 1, 2, 4, 6, 7}");
    }

    @Test
    public void testSet() {
        RBitSet bs = redisson.getBitSet("testbitset");
        assertThat(bs.set(3)).isFalse();
        assertThat(bs.set(5)).isFalse();
        assertThat(bs.set(5)).isTrue();
        assertThat(bs.toString()).isEqualTo("{3, 5}");

        BitSet bs1 = new BitSet();
        bs1.set(1);
        bs1.set(10);
        bs.set(bs1);

        bs = redisson.getBitSet("testbitset");
        assertThat(bs.toString()).isEqualTo("{1, 10}");
    }

    @Test
    public void testSetGet() {
        RBitSet bitset = redisson.getBitSet("testbitset");
        assertThat(bitset.cardinality()).isZero();
        assertThat(bitset.size()).isZero();

        bitset.set(10, true);
        bitset.set(31, true);
        assertThat(bitset.get(0)).isFalse();
        assertThat(bitset.get(31)).isTrue();
        assertThat(bitset.get(10)).isTrue();
        assertThat(bitset.cardinality()).isEqualTo(2);
        assertThat(bitset.size()).isEqualTo(32);
    }

    @Test
    public void testSetRange() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(3, 10);
        assertThat(bs.cardinality()).isEqualTo(7);
        assertThat(bs.size()).isEqualTo(16);
    }

    @Test
    public void testAsBitSet() {
        RBitSet bs = redisson.getBitSet("testbitset");
        bs.set(3, true);
        bs.set(41, true);
        assertThat(bs.size()).isEqualTo(48);

        BitSet bitset = bs.asBitSet();
        assertThat(bitset.get(3)).isTrue();
        assertThat(bitset.get(41)).isTrue();
        assertThat(bs.cardinality()).isEqualTo(2);
        
        RBitSet emptyBitSet = redisson.getBitSet("emptybitset");
        BitSet s = emptyBitSet.asBitSet();
        assertThat(s.cardinality()).isZero();
    }

    @Test
    public void testAnd() {
        RBitSet bs1 = redisson.getBitSet("testbitset1");
        bs1.set(3, 5);
        assertThat(bs1.cardinality()).isEqualTo(2);
        assertThat(bs1.size()).isEqualTo(8);

        RBitSet bs2 = redisson.getBitSet("testbitset2");
        bs2.set(4);
        bs2.set(10);
        bs1.and(bs2.getName());
        assertThat(bs1.get(3)).isFalse();
        assertThat(bs1.get(4)).isTrue();
        assertThat(bs1.get(5)).isFalse();
        assertThat(bs2.get(10)).isTrue();

        assertThat(bs1.cardinality()).isEqualTo(1);
        assertThat(bs1.size()).isEqualTo(16);
    }


}
