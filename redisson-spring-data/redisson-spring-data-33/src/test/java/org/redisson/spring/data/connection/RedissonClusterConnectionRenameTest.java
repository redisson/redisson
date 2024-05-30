package org.redisson.spring.data.connection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.redisson.BaseTest;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.redisson.connection.MasterSlaveConnectionManager.MAX_SLOT;

@RunWith(Parameterized.class)
public class RedissonClusterConnectionRenameTest extends BaseTest {

    @Parameterized.Parameters(name= "{index} - same slot = {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {false},
                {true}
        });
    }

    @Parameterized.Parameter(0)
    public boolean sameSlot;

    byte[] originalKey = "key".getBytes();
    byte[] newKey = "unset".getBytes();
    byte[] value = "value".getBytes();

    @Test
    public void testRename() {
        testInCluster(connection -> {
            connection.set(originalKey, value);
            connection.expire(originalKey, 1000);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(originalSlot), connection);

            connection.rename(originalKey, newKey);

            assertThat(connection.get(newKey)).isEqualTo(value);
            assertThat(connection.ttl(newKey)).isGreaterThan(0);
        });
    }

    @Test
    public void testRename_pipeline() {
        testInCluster(connection -> {
            connection.set(originalKey, value);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(originalSlot), connection);

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

    @Test
    public void testRenameNX() {
        testInCluster(connection -> {
            connection.set(originalKey, value);
            connection.expire(originalKey, 1000);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(originalSlot), connection);

            Boolean result = connection.renameNX(originalKey, newKey);

            assertThat(connection.get(newKey)).isEqualTo(value);
            assertThat(connection.ttl(newKey)).isGreaterThan(0);
            assertThat(result).isTrue();

            connection.set(originalKey, value);

            result = connection.renameNX(originalKey, newKey);

            assertThat(result).isFalse();
        });
    }

    @Test
    public void testRenameNX_pipeline() {
        testInCluster(connection -> {
            connection.set(originalKey, value);

            Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
            newKey = getNewKeyForSlot(originalKey, getTargetSlot(originalSlot), connection);

            connection.openPipeline();
            assertThatThrownBy(() -> connection.renameNX(originalKey, newKey)).isInstanceOf(InvalidDataAccessResourceUsageException.class);
            connection.closePipeline();
        });
    }

    private Integer getTargetSlot(Integer originalSlot) {
        return sameSlot ? originalSlot : MAX_SLOT - originalSlot - 1;
    }

}
