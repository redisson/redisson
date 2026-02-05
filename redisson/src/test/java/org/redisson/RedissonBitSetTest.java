package org.redisson;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.bitset.BitFieldArgs;
import org.redisson.api.bitset.BitFieldOverflow;
import org.redisson.api.bitset.BitOffset;
import org.redisson.api.RBitSet;

import java.util.BitSet;
import java.util.List;
import org.redisson.api.listener.TrackingListener;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBitSetTest extends RedisDockerTest {

    @Test
    public void testUnsigned() {
        RBitSet bs = redisson.getBitSet("testUnsigned");
        assertThat(bs.setUnsigned(8, 1, 120)).isZero();
        assertThat(bs.incrementAndGetUnsigned(8, 1, 1)).isEqualTo(121);
        assertThat(bs.getUnsigned(8, 1)).isEqualTo(121);
    }

    @Test
    public void testSigned() {
        RBitSet bs = redisson.getBitSet("testSigned");
        assertThat(bs.setSigned(8, 1, -120)).isZero();
        assertThat(bs.incrementAndGetSigned(8, 1, 1)).isEqualTo(-119);
        assertThat(bs.getSigned(8, 1)).isEqualTo(-119);
    }

    @Test
    public void testBitFieldMultipleOperations() {
        RBitSet bs = redisson.getBitSet("testBitFieldMultipleOperations");

        List<Long> result = bs.bitField(BitFieldArgs.create()
                .incrementUnsignedBy(32, BitOffset.bit(32), 1)
                .incrementUnsignedBy(32, BitOffset.bit(64), 1)
                .getUnsigned(32, BitOffset.bit(32)));
        assertThat(result).containsExactly(1L, 1L, 1L);

        result = bs.bitField(BitFieldArgs.create()
                .incrementSignedBy(8, BitOffset.bit(0), -1)
                .incrementUnsignedBy(8, BitOffset.bit(8), 2));
        assertThat(result).containsExactly(-1L, 2L);
    }

    @Test
    public void testBitFieldIndexedOffsets() {
        RBitSet bs = redisson.getBitSet("testBitFieldIndexedOffsets");

        List<Long> result = bs.bitField(BitFieldArgs.create()
                .setSigned(8, BitOffset.index(0), 100)
                .setSigned(8, BitOffset.index(1), 200)
                .getUnsigned(8, BitOffset.bit(0))
                .getUnsigned(8, BitOffset.bit(8)));
        assertThat(result).containsExactly(0L, 0L, 100L, 200L);
    }

    @Test
    public void testBitFieldOverflowFail() {
        RBitSet bs = redisson.getBitSet("testBitFieldOverflowFail");

        List<Long> result = bs.bitField(BitFieldArgs.create()
                .setUnsigned(2, BitOffset.bit(102), 3)
                                                    .overflow(BitFieldOverflow.FAIL)
                .incrementUnsignedBy(2, BitOffset.bit(102), 1));
        assertThat(result).containsExactly(0L, null);
    }

    @Test
    public void testBitFieldReadOnly() {
        testInCluster(rc -> {
            Config c = rc.getConfig();
            c.useClusterServers().setReadMode(ReadMode.SLAVE);
            RedissonClient redissonClient = Redisson.create(c);

            RBitSet bs = redissonClient.getBitSet("testBitFieldReadOnly");
            List<Long> set = bs.bitField(BitFieldArgs.create()
                    .setSigned(8, BitOffset.index(0), 100)
                    .setSigned(8, BitOffset.index(1), 200));
            assertThat(set).containsExactly(0L, 0L);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            List<Long> result = bs.bitField(BitFieldArgs.create()
                    .getUnsigned(8, BitOffset.bit(0))
                    .getUnsigned(8, BitOffset.bit(8)));

            assertThat(result).containsExactly(100L, 200L);

            redissonClient.shutdown();
        });
    }

    @Test
    public void testIncrement() {
        RBitSet bs2 = redisson.getBitSet("testbitset1");
        assertThat(bs2.setByte(2, (byte)12)).isZero();
        assertThat(bs2.getByte(2)).isEqualTo((byte)12);

        assertThat(bs2.incrementAndGetByte(2, (byte)12)).isEqualTo((byte) 24);
        assertThat(bs2.getByte(2)).isEqualTo((byte)24);
    }

    @Test
    public void testSetGetNumber() {
        RBitSet bs = redisson.getBitSet("testbitset");
        assertThat(bs.setLong(2, 12L)).isZero();
        assertThat(bs.getLong(2)).isEqualTo(12);

        RBitSet bs2 = redisson.getBitSet("testbitset1");
        assertThat(bs2.setByte(2, (byte)12)).isZero();
        assertThat(bs2.getByte(2)).isEqualTo((byte)12);

        RBitSet bs3 = redisson.getBitSet("testbitset2");
        assertThat(bs3.setShort(2, (short)2312)).isZero();
        assertThat(bs3.getShort(2)).isEqualTo((short)2312);

        RBitSet bs4 = redisson.getBitSet("testbitset3");
        assertThat(bs4.setInteger(2, 323241)).isZero();
        assertThat(bs4.getInteger(2)).isEqualTo(323241);
    }

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
        
        // no exception happened, but return is unexpected.
        bs.clear();
        bs.set(0, 2);
        bs.set(9);
        bs.clear(9, 10);
        assertThat(bs.length()).isEqualTo(2);
        
        // cause exception
        bs.clear();
        bs.set(7);
        bs.set(9);
        bs.clear(9, 10);
        assertThat(bs.length()).isEqualTo(8);
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
        assertThat(bs.not()).isEqualTo(1);
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

        RBitSet bs2 = redisson.getBitSet("testbitset2");
        bs2.set(new long[]{1L,3L,5L,7L}, true);
        bs2 = redisson.getBitSet("testbitset2");
        assertThat(bs2.toString()).isEqualTo("{1, 3, 5, 7}");

        bs2.set(new long[]{3L,5L}, false);
        bs2 = redisson.getBitSet("testbitset2");
        assertThat(bs2.toString()).isEqualTo("{1, 7}");
    }

    @Test
    public void testSetGet() {
        RBitSet bitset = redisson.getBitSet("testbitset");
        assertThat(bitset.cardinality()).isZero();
        assertThat(bitset.size()).isZero();

        assertThat(bitset.set(10, true)).isFalse();
        assertThat(bitset.set(31, true)).isFalse();
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
        assertThat(bs1.and(bs2.getName())).isEqualTo(2);
        assertThat(bs1.get(3)).isFalse();
        assertThat(bs1.get(4)).isTrue();
        assertThat(bs1.get(5)).isFalse();
        assertThat(bs2.get(10)).isTrue();

        assertThat(bs1.cardinality()).isEqualTo(1);
        assertThat(bs1.size()).isEqualTo(16);
    }

    @Test
    public void testGetWithIndexes() {
        RBitSet bitset = redisson.getBitSet("testbitset");
        
        bitset.set(4, 10);
        boolean[] result = bitset.get(2, 4, 7, 8);
        assertThat(result[0]).isFalse();
        assertThat(result[1]).isTrue();
        assertThat(result[2]).isTrue();
        assertThat(result[3]).isTrue();
    }

    @Test
    public void testAndOr() {
        RBitSet bs1 = redisson.getBitSet("testbitset1");
        bs1.set(3, 5);
        assertThat(bs1.cardinality()).isEqualTo(2);
        assertThat(bs1.size()).isEqualTo(8);

        RBitSet bs2 = redisson.getBitSet("testbitset2");
        bs2.set(4);
        bs2.set(10);
        assertThat(bs1.andOr(bs2.getName())).isEqualTo(2);
        assertThat(bs1.get(3)).isFalse();
        assertThat(bs1.get(4)).isTrue();
        assertThat(bs1.get(5)).isFalse();
        assertThat(bs2.get(10)).isTrue();

        assertThat(bs1.cardinality()).isEqualTo(1);
        assertThat(bs1.size()).isEqualTo(16);
    }

    @Test
    public void testDiff() {
        RBitSet bs1 = redisson.getBitSet("testbitset1");
        bs1.set(3, 5);
        assertThat(bs1.cardinality()).isEqualTo(2);
        assertThat(bs1.size()).isEqualTo(8);

        RBitSet bs2 = redisson.getBitSet("testbitset2");
        bs2.set(4);
        bs2.set(10);
        assertThat(bs1.diff(bs2.getName())).isEqualTo(2);
        assertThat(bs1.get(3)).isTrue();
        assertThat(bs1.get(4)).isFalse();
        assertThat(bs1.get(5)).isFalse();
        assertThat(bs2.get(10)).isTrue();

        assertThat(bs1.cardinality()).isEqualTo(1);
        assertThat(bs1.size()).isEqualTo(16);
    }


}
