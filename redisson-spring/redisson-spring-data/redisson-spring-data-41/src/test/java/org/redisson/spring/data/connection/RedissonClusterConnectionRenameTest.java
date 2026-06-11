package org.redisson.spring.data.connection;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.RedisDockerTest;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.redisson.connection.MasterSlaveConnectionManager.MAX_SLOT;

public class RedissonClusterConnectionRenameTest extends RedisDockerTest {

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
