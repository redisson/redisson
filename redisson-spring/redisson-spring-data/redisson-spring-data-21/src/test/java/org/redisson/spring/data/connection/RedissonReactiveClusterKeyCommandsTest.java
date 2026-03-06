package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.*;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;


import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.redisson.connection.MasterSlaveConnectionManager.MAX_SLOT;

public class RedissonReactiveClusterKeyCommandsTest extends RedisDockerTest {

    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {false, false},
                {true, false},
                {false, true},
                {true, true}
        });
    }

    ByteBuffer originalKey = ByteBuffer.wrap("key".getBytes());
    ByteBuffer newKey = ByteBuffer.wrap("unset".getBytes());
    ByteBuffer value = ByteBuffer.wrap("value".getBytes());

    private void testInClusterReactive(Consumer<ReactiveRedisClusterConnection> redissonCallback) {
        testInCluster(c -> {
            RedissonClient redisson = (RedissonClient) c.getNativeConnection();
            RedissonReactiveRedisClusterConnection connection = new RedissonReactiveRedisClusterConnection(((RedissonReactive) redisson.reactive()).getCommandExecutor());
            redissonCallback.accept(connection);
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRename(boolean sameSlot, boolean hasTtl) {
        testInClusterReactive(connection -> {
            connection.stringCommands().set(originalKey, value).block();

            if (hasTtl) {
                connection.keyCommands().expire(originalKey, Duration.ofSeconds(1000)).block();
            }

            Integer originalSlot = getSlotForKey(originalKey, (RedissonReactiveRedisClusterConnection) connection);
            newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(sameSlot, originalSlot), connection);

            Boolean response = connection.keyCommands().rename(originalKey, newKey).block();

            assertThat(response).isTrue();

            final ByteBuffer newKeyValue = connection.stringCommands().get(newKey).block();
            assertThat(newKeyValue).isEqualTo(value);
            if (hasTtl) {
                assertThat(connection.keyCommands().ttl(newKey).block()).isGreaterThan(0);
            } else {
                assertThat(connection.keyCommands().ttl(newKey).block()).isEqualTo(-1);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRename_keyNotExist(boolean sameSlot, boolean hasTtl) {
        testInClusterReactive(connection -> {
            Integer originalSlot = getSlotForKey(originalKey, (RedissonReactiveRedisClusterConnection) connection);
            newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(sameSlot, originalSlot), connection);

            if (sameSlot) {
                // This is a quirk of the implementation - since same-slot renames use the non-cluster version,
                // the result is a Redis error. This behavior matches other spring-data-redis implementations
                assertThatThrownBy(() -> connection.keyCommands().rename(originalKey, newKey).block())
                        .isInstanceOf(RedisSystemException.class);

            } else {
                Boolean response = connection.keyCommands().rename(originalKey, newKey).block();

                assertThat(response).isTrue();

                final ByteBuffer newKeyValue = connection.stringCommands().get(newKey).block();
                assertThat(newKeyValue).isEqualTo(null);
            }
        });
    }

    protected ByteBuffer getNewKeyForSlot(String originalKey, Integer targetSlot, ReactiveRedisClusterConnection connection) {
        int counter = 0;

        ByteBuffer newKey = ByteBuffer.wrap((originalKey + counter).getBytes());

        Integer newKeySlot = getSlotForKey(newKey, (RedissonReactiveRedisClusterConnection) connection);

        while(!newKeySlot.equals(targetSlot)) {
            counter++;
            newKey = ByteBuffer.wrap((originalKey + counter).getBytes());
            newKeySlot = getSlotForKey(newKey, (RedissonReactiveRedisClusterConnection) connection);
        }

        return newKey;
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRenameNX(boolean sameSlot, boolean hasTtl) {
        testInClusterReactive(connection -> {
            connection.stringCommands().set(originalKey, value).block();
            if (hasTtl) {
                connection.keyCommands().expire(originalKey, Duration.ofSeconds(1000)).block();
            }

            Integer originalSlot = getSlotForKey(originalKey, (RedissonReactiveRedisClusterConnection) connection);
            newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(sameSlot, originalSlot), connection);

            Boolean result = connection.keyCommands().renameNX(originalKey, newKey).block();

            assertThat(result).isTrue();
            assertThat(connection.stringCommands().get(newKey).block()).isEqualTo(value);
            if (hasTtl) {
                assertThat(connection.keyCommands().ttl(newKey).block()).isGreaterThan(0);
            } else {
                assertThat(connection.keyCommands().ttl(newKey).block()).isEqualTo(-1);
            }

            connection.stringCommands().set(originalKey, value).block();

            result = connection.keyCommands().renameNX(originalKey, newKey).block();

            assertThat(result).isFalse();
        });
    }

    private Integer getTargetSlot(boolean sameSlot, Integer originalSlot) {
        return sameSlot ? originalSlot : MAX_SLOT - originalSlot - 1;
    }
    
    private Integer getSlotForKey(ByteBuffer key, RedissonReactiveRedisClusterConnection connection) {
        return (Integer) connection.read(null, StringCodec.INSTANCE, RedisCommands.KEYSLOT, key.array()).block();
    }

}
