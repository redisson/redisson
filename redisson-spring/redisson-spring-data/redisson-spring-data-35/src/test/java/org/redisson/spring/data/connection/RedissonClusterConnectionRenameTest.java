package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.RedisDockerTest;
import org.redisson.api.RedissonClient;
import org.redisson.api.redisnode.RedisClusterSlave;
import org.redisson.api.redisnode.RedisNode;
import org.redisson.api.redisnode.RedisNodes;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.redisson.connection.MasterSlaveConnectionManager.MAX_SLOT;

public class RedissonClusterConnectionRenameTest extends RedisDockerTest {

    private static final Pattern CALLS = Pattern.compile("calls=(\\d+)");

    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {false},
                {true}
        });
    }

    byte[] originalKey = "key".getBytes();
    byte[] newKey = "unset".getBytes();
    byte[] value = "value".getBytes();

    @ParameterizedTest
    @MethodSource("data")
    public void testRename(boolean sameSlot) {
        testInCluster(connection -> {
            connection.set(originalKey, value);
            connection.expire(originalKey, 1000);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(sameSlot, originalSlot), connection);

            connection.rename(originalKey, newKey);

            assertThat(connection.get(newKey)).isEqualTo(value);
            assertThat(connection.ttl(newKey)).isGreaterThan(0);
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRenameOverwritesTarget(boolean sameSlot) {
        testInCluster(connection -> {
            connection.set(originalKey, value);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(sameSlot, originalSlot), connection);
            connection.set(newKey, "existing".getBytes());

            connection.rename(originalKey, newKey);

            Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                assertThat(connection.get(newKey)).isEqualTo(value);
                assertThat(connection.exists(originalKey)).isFalse();
            });
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRename_pipeline(boolean sameSlot) {
        testInCluster(connection -> {
            connection.set(originalKey, value);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(sameSlot, originalSlot), connection);

            connection.openPipeline();
            assertThatThrownBy(() -> connection.rename(originalKey, newKey)).isInstanceOf(InvalidDataAccessResourceUsageException.class);
            connection.closePipeline();
        });
    }

    @Test
    public void testRenameReadsTheSourceFromTheMaster() {
        testInCluster(connection -> {
            RedissonClient redisson = (RedissonClient) connection.getNativeConnection();
            connection.set(originalKey, value);
            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(false, originalSlot), connection);
            long replicaReads = replicaReads(redisson);

            connection.pExpire(originalKey, 499);
            connection.rename(originalKey, newKey);
            assertThat((Long) connection.execute("PTTL", newKey)).isBetween(1L, 499L);
            assertThat(connection.renameNX(newKey, originalKey)).isTrue();
            assertThat((Long) connection.execute("PTTL", originalKey)).isBetween(1L, 499L);

            assertThat(replicaReads(redisson)).isEqualTo(replicaReads);
        });
    }

    private long replicaReads(RedissonClient redisson) {
        long calls = 0;
        for (RedisClusterSlave replica : redisson.getRedisNodes(RedisNodes.CLUSTER).getSlaves()) {
            Map<String, String> stats = replica.info(RedisNode.InfoSection.COMMANDSTATS);
            for (String command : Arrays.asList("dump", "ttl", "pttl", "exists")) {
                String stat = stats.get("cmdstat_" + command);
                if (stat != null) {
                    Matcher matcher = CALLS.matcher(stat);
                    assertThat(matcher.find()).isTrue();
                    calls += Long.parseLong(matcher.group(1));
                }
            }
        }
        return calls;
    }

    protected byte[] getNewKeyForSlot(byte[] originalKey, Integer targetSlot, RedissonClusterConnection connection) {
        int counter = 0;

        byte[] newKey = (new String(originalKey) + counter).getBytes();

        Integer newKeySlot = connection.clusterGetSlotForKey(newKey);

        while(!newKeySlot.equals(targetSlot)) {
            counter++;
            newKey = (new String(originalKey) + counter).getBytes();
            newKeySlot = connection.clusterGetSlotForKey(newKey);
        }

        return newKey;
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRenameNX(boolean sameSlot) {
        testInCluster(connection -> {
            connection.set(originalKey, value);
            connection.expire(originalKey, 1000);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(sameSlot, originalSlot), connection);

            Boolean result = connection.renameNX(originalKey, newKey);

            assertThat(connection.get(newKey)).isEqualTo(value);
            assertThat(connection.ttl(newKey)).isGreaterThan(0);
            assertThat(result).isTrue();

            connection.set(originalKey, value);

            result = connection.renameNX(originalKey, newKey);

            assertThat(result).isFalse();
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRenameNX_pipeline(boolean sameSlot) {
        testInCluster(connection -> {
            connection.set(originalKey, value);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(sameSlot, originalSlot), connection);

            connection.openPipeline();
            assertThatThrownBy(() -> connection.renameNX(originalKey, newKey)).isInstanceOf(InvalidDataAccessResourceUsageException.class);
            connection.closePipeline();
        });
    }

    private Integer getTargetSlot(boolean sameSlot, Integer originalSlot) {
        return sameSlot ? originalSlot : MAX_SLOT - originalSlot - 1;
    }

}
