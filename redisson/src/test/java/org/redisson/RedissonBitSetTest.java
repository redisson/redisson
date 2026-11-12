package org.redisson;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.api.RBatch;
import org.redisson.api.RBitSetAsync;
import org.redisson.api.RBitSetReactive;
import org.redisson.api.RBitSetRx;
import org.redisson.api.RFuture;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.bitset.BitFieldArgs;
import org.redisson.api.bitset.BitFieldOverflow;
import org.redisson.api.bitset.BitOffset;
import org.redisson.api.bitset.BitRangeArgs;
import org.redisson.api.redisnode.RedisNode;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.client.RedisException;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.redisson.api.RBitSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.redisson.api.listener.TrackingListener;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonBitSetTest extends RedisDockerTest {

    @ParameterizedTest
    @ValueSource(strings = {"redis:5.0.14", IMAGE})
    public void testNumericReadsWithSlaveReadMode(String image) throws Exception {
        try (Network network = Network.newNetwork();
             GenericContainer<?> master = createContainerByImage(image).withNetwork(network).withNetworkAliases("master");
             GenericContainer<?> slave = createContainerByImage(image, "--replicaof", "master", "6379").withNetwork(network)) {
            master.start();
            slave.start();

            Config config = new Config();
            config.useMasterSlaveServers()
                    .setMasterAddress("redis://" + master.getHost() + ":" + master.getFirstMappedPort())
                    .addSlaveAddress("redis://" + slave.getHost() + ":" + slave.getFirstMappedPort())
                    .setReadMode(ReadMode.SLAVE)
                    .setRetryAttempts(0);
            RedissonClient client = Redisson.create(config);
            try {
                RBitSet bitSet = client.getBitSet("numericReads");
                bitSet.setLong(0, -1L);
                Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                        assertThat(bitSet.get(0L)).isTrue());

                assertThat(bitSet.getSigned(8, 0)).isEqualTo(-1L);
                assertThat(bitSet.getUnsigned(8, 0)).isEqualTo(255L);
                assertThat(bitSet.getByte(0)).isEqualTo((byte) -1);
                assertThat(bitSet.getShort(0)).isEqualTo((short) -1);
                assertThat(bitSet.getInteger(0)).isEqualTo(-1);
                assertThat(bitSet.getLong(0)).isEqualTo(-1L);
                assertThat(bitSet.get(0L, 63L, 64L)).containsExactly(true, true, false);
                assertThatThrownBy(() -> bitSet.getSigned(0, 0))
                        .isInstanceOf(RedisException.class)
                        .hasMessageContaining("Invalid bitfield type");

                String masterStats = master.execInContainer("redis-cli", "INFO", "commandstats").getStdout();
                String slaveStats = slave.execInContainer("redis-cli", "INFO", "commandstats").getStdout();
                if (image.equals(IMAGE)) {
                    assertThat(masterStats).contains("cmdstat_bitfield:calls=1,");
                    assertThat(slaveStats).contains("cmdstat_bitfield_ro:calls=8,");
                } else {
                    assertThat(masterStats).contains("cmdstat_bitfield:calls=9,");
                }
            } finally {
                client.shutdown();
            }
        }
    }

    @Test
    public void testNumericReadsWithMasterReadMode() {
        // single server mode reads with the master read mode
        RedisNode node = redisson.getRedisNodes(RedisNodes.SINGLE).getInstance();
        String before = node.info(RedisNode.InfoSection.COMMANDSTATS).get("cmdstat_bitfield_ro");

        RBitSet bitSet = redisson.getBitSet("masterReads");
        bitSet.setLong(0, -1L);
        assertThat(bitSet.getLong(0)).isEqualTo(-1L);
        assertThat(bitSet.get(0L, 63L, 64L)).containsExactly(true, true, false);
        assertThat(bitSet.bitField(BitFieldArgs.create().getUnsigned(8, BitOffset.bit(0))))
                .containsExactly(255L);

        assertThat(node.info(RedisNode.InfoSection.COMMANDSTATS).get("cmdstat_bitfield_ro"))
                .isEqualTo(before);
    }

    @Test
    public void testBitFieldWritesOnReadPath() {
        // slave read mode enables BITFIELD_RO for reads, without replicas they are served by the master
        Config config = new Config();
        config.useReplicatedServers()
                .addNodeAddress(redisson.getConfig().useSingleServer().getAddress())
                .setReadMode(ReadMode.SLAVE);
        RedissonClient client = Redisson.create(config);
        try {
            // older Spring Data modules send BITFIELD with write subcommands as a read command
            CommandAsyncExecutor executor = ((Redisson) client).getCommandExecutor();
            RFuture<List<Long>> written = executor.readAsync("readPathWrites", LongCodec.INSTANCE,
                    RedisCommands.BITFIELD_LONG_LIST, "readPathWrites",
                    "GET", "u8", 0, "SET", "u8", 0, 100, "SET", "u8", 8, 200, "INCRBY", "u8", 16, 1);
            assertThat(executor.get(written)).containsExactly(0L, 0L, 0L, 1L);

            assertThat(client.getBitSet("readPathWrites").getUnsigned(8, 8)).isEqualTo(200L);
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testBitFieldWithMasterSlaveReadMode() throws Exception {
        try (GenericContainer<?> node = createContainer()) {
            node.start();

            // master slave read mode enables BITFIELD_RO for reads, without replicas they are served by the master
            Config config = new Config();
            config.useReplicatedServers()
                    .addNodeAddress("redis://" + node.getHost() + ":" + node.getFirstMappedPort())
                    .setReadMode(ReadMode.MASTER_SLAVE);
            RedissonClient client = Redisson.create(config);
            try {
                RBitSet bitSet = client.getBitSet("masterSlaveReads");
                bitSet.setLong(0, -1L);

                assertThat(bitSet.bitField(BitFieldArgs.create().getUnsigned(8, BitOffset.bit(0))))
                        .containsExactly(255L);
                assertThat(node.execInContainer("redis-cli", "INFO", "commandstats").getStdout())
                        .contains("cmdstat_bitfield_ro:calls=1,");
            } finally {
                client.shutdown();
            }
        }
    }

    @Test
    public void testBitFieldROFallbackIsRemembered() throws Exception {
        try (Network network = Network.newNetwork();
             GenericContainer<?> master = createContainer("--rename-command", "BITFIELD_RO", "BITFIELD_RO_DISABLED")
                     .withNetwork(network).withNetworkAliases("master");
             GenericContainer<?> slave = createContainer("--replicaof", "master", "6379",
                     "--rename-command", "BITFIELD_RO", "BITFIELD_RO_DISABLED").withNetwork(network)) {
            master.start();
            slave.start();

            Config config = new Config();
            config.useMasterSlaveServers()
                    .setMasterAddress("redis://" + master.getHost() + ":" + master.getFirstMappedPort())
                    .addSlaveAddress("redis://" + slave.getHost() + ":" + slave.getFirstMappedPort())
                    .setReadMode(ReadMode.SLAVE)
                    .setRetryAttempts(0);
            RedissonClient client = Redisson.create(config);
            try {
                RBitSet bitSet = client.getBitSet("fallbackReads");
                bitSet.setLong(0, -1L);
                Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                        assertThat(bitSet.get(0L)).isTrue());

                long slaveErrors = errorReplies(slave);
                for (int i = 0; i < 5; i++) {
                    assertThat(bitSet.getLong(0)).isEqualTo(-1L);
                }

                assertThat(errorReplies(slave) - slaveErrors).isEqualTo(1);
                assertThat(master.execInContainer("redis-cli", "INFO", "commandstats").getStdout())
                        .contains("cmdstat_bitfield:calls=6,");
            } finally {
                client.shutdown();
            }
        }
    }

    @Test
    public void testBitFieldROFallbackWithoutPermission() throws Exception {
        try (GenericContainer<?> node = createContainer()) {
            node.start();
            node.execInContainer("redis-cli", "ACL", "SETUSER", "nobitfieldro", "on", ">secret", "~*",
                    "+@all", "-bitfield_ro");

            // slave read mode enables BITFIELD_RO for reads, without replicas they are served by the master
            Config config = new Config();
            config.setUsername("nobitfieldro").setPassword("secret");
            config.useReplicatedServers()
                    .addNodeAddress("redis://" + node.getHost() + ":" + node.getFirstMappedPort())
                    .setReadMode(ReadMode.SLAVE);
            RedissonClient client = Redisson.create(config);
            try {
                RBitSet bitSet = client.getBitSet("deniedReads");
                bitSet.setLong(0, -1L);

                long errors = errorReplies(node);
                for (int i = 0; i < 5; i++) {
                    assertThat(bitSet.getLong(0)).isEqualTo(-1L);
                }

                assertThat(errorReplies(node) - errors).isEqualTo(1);
            } finally {
                client.shutdown();
            }
        }
    }

    private long errorReplies(GenericContainer<?> container) throws Exception {
        String stats = container.execInContainer("redis-cli", "INFO", "stats").getStdout();
        Matcher matcher = Pattern.compile("total_error_replies:(\\d+)").matcher(stats);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    @Test
    public void testCancelBitFieldReadWaitingForConnection() throws Exception {
        // slave read mode enables BITFIELD_RO for reads, without replicas they wait for the only master connection
        Config config = new Config();
        config.useReplicatedServers()
                .addNodeAddress(redisson.getConfig().useSingleServer().getAddress())
                .setReadMode(ReadMode.SLAVE)
                .setMasterConnectionMinimumIdleSize(1)
                .setMasterConnectionPoolSize(1);
        RedissonClient client = Redisson.create(config);
        try {
            RedisNode node = client.getRedisNodes(RedisNodes.MASTER_SLAVE).getMaster();
            String before = node.info(RedisNode.InfoSection.COMMANDSTATS).get("cmdstat_bitfield_ro");
            RFuture<String> blocking = client.<String>getBlockingQueue("holdConnection").takeAsync();

            RFuture<Byte> read = client.getBitSet("bits").getByteAsync(0);
            assertThat(read.cancel(false)).isTrue();
            redisson.getBlockingQueue("holdConnection").add("release");
            blocking.toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertThat(node.info(RedisNode.InfoSection.COMMANDSTATS).get("cmdstat_bitfield_ro"))
                    .isEqualTo(before);
        } finally {
            client.shutdown();
        }
    }

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

            Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                List<Long> result = bs.bitField(BitFieldArgs.create()
                        .getUnsigned(8, BitOffset.bit(0))
                        .getUnsigned(8, BitOffset.bit(8)));
                assertThat(result).containsExactly(100L, 200L);
            });

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

    private static GenericContainer<?> redis812;
    private static RedissonClient redisson812;

    @BeforeAll
    static void startRedis812() {
        redis812 = createRedis812();
        redis812.start();
        redisson812 = Redisson.create(createConfig(redis812));
    }

    @AfterAll
    static void stopRedis812() {
        redisson812.shutdown();
        redis812.stop();
    }

    @Test
    public void testGetIndexesByBytes() {
        RBitSet bs = redisson812.getBitSet("testGetIndexesByBytes");
        bs.set(new long[] {3, 10, 17}, true);

        assertThat(bs.getIndexes(BitRangeArgs.bytes(0, -1))).containsExactly(3, 10, 17);
        assertThat(bs.getIndexes(BitRangeArgs.bytes(1, 1))).containsExactly(10);
        assertThat(bs.getIndexes(BitRangeArgs.bytes(-1, -1))).containsExactly(17);

        assertThat(bs.getIndexes(BitRangeArgs.bytes(2, 100))).containsExactly(17);
        assertThat(bs.getIndexes(BitRangeArgs.bytes(1, 0))).isEmpty();
        assertThat(bs.getIndexes(BitRangeArgs.bytes(3, 5))).isEmpty();

        RBitSet missing = redisson812.getBitSet("testGetIndexesMissing");
        assertThat(missing.getIndexes(BitRangeArgs.bytes(0, -1))).isEmpty();
    }

    @Test
    public void testGetIndexesByBits() {
        RBitSet bs = redisson812.getBitSet("testGetIndexesByBits");
        bs.set(new long[] {3, 10, 17}, true);

        assertThat(bs.getIndexes(BitRangeArgs.bits(4, 17))).containsExactly(10, 17);
        assertThat(bs.getIndexes(BitRangeArgs.bits(3, 3))).containsExactly(3);
        assertThat(bs.getIndexes(BitRangeArgs.bits(11, 16))).isEmpty();

        assertThat(bs.getIndexes(BitRangeArgs.bits(-8, -1))).containsExactly(17);

        assertThat(bs.getIndexes(BitRangeArgs.bits(1, 1))).isEmpty();
        assertThat(bs.getIndexes(BitRangeArgs.bytes(1, 1))).containsExactly(10);
    }

    @Test
    public void testGetIndexesZeros() {
        RBitSet bs = redisson812.getBitSet("testGetIndexesZeros");
        bs.set(new long[] {3, 10, 17}, true);

        assertThat(bs.getIndexes(BitRangeArgs.bits(0, 7).zeros())).containsExactly(0, 1, 2, 4, 5, 6, 7);
        assertThat(bs.getIndexes(BitRangeArgs.bytes(2, 2).zeros())).containsExactly(16, 18, 19, 20, 21, 22, 23);


        long[] zeros = bs.getIndexes(BitRangeArgs.bytes(0, -1).zeros());
        assertThat(zeros).hasSize(21).doesNotContain(3, 10, 17);
        assertThat(zeros[zeros.length - 1]).isEqualTo(23);
        assertThat(bs.getIndexes(BitRangeArgs.bits(0, 1000).zeros())).hasSize(21);

        RBitSet missing = redisson812.getBitSet("testGetIndexesZerosMissing");
        assertThat(missing.getIndexes(BitRangeArgs.bytes(0, -1).zeros())).isEmpty();
    }

    @Test
    public void testGetIndexesLimitByBits() {
        RBitSet bs = redisson812.getBitSet("testGetIndexesLimitByBits");
        bs.set(new long[] {0, 1, 2, 9, 30}, true);

        assertThat(bs.getIndexes(BitRangeArgs.bits(0, -1).limit(2))).containsExactly(0, 1);
        assertThat(bs.getIndexes(BitRangeArgs.bits(0, -1).limit(0))).isEmpty();
        assertThat(bs.getIndexes(BitRangeArgs.bits(0, -1).limit(1).limit(3))).containsExactly(0, 1, 2);

        List<Long> all = new ArrayList<>();
        long start = 0;
        while (true) {
            long[] page = bs.getIndexes(BitRangeArgs.bits(start, -1).limit(2));
            assertThat(page.length).isLessThanOrEqualTo(2);
            for (long index : page) {
                all.add(index);
            }
            if (page.length < 2) {
                break;
            }
            start = page[page.length - 1] + 1;
        }
        assertThat(all).containsExactly(0L, 1L, 2L, 9L, 30L);
    }

    @Test
    public void testGetIndexesLimitByBytes() {
        RBitSet bs = redisson812.getBitSet("testGetIndexesLimitByBytes");
        bs.set(new long[] {0, 1, 2, 9, 30}, true);

        assertThat(bs.getIndexes(BitRangeArgs.bytes(0, -1).limit(1))).containsExactly(0, 1, 2);
        assertThat(bs.getIndexes(BitRangeArgs.bytes(0, -1).limit(4))).containsExactly(0, 1, 2, 9);

        List<Long> all = new ArrayList<>();
        long startByte = 0;
        while (true) {
            long[] page = bs.getIndexes(BitRangeArgs.bytes(startByte, -1).limit(1));
            if (page.length == 0) {
                break;
            }
            for (long index : page) {
                all.add(index);
            }
            startByte = (page[page.length - 1] >> 3) + 1;
        }
        assertThat(all).containsExactly(0L, 1L, 2L, 9L, 30L);
    }

    @Test
    public void testGetIndexesFeedsSet() {
        RBitSet bs = redisson812.getBitSet("testGetIndexesFeedsSet");
        bs.set(new long[] {5, 64, 100, 1000}, true);

        long[] indexes = bs.getIndexes(BitRangeArgs.bytes(0, -1));
        assertThat(bs.get(indexes)).containsOnly(true);
        bs.set(indexes, false);
        assertThat(bs.cardinality()).isZero();
        assertThat(bs.getIndexes(BitRangeArgs.bytes(0, -1))).isEmpty();
    }


}
