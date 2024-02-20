package org.redisson.spring.data.connection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.redisson.*;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.redisson.reactive.CommandReactiveService;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.redisson.connection.MasterSlaveConnectionManager.MAX_SLOT;

@RunWith(Parameterized.class)
public class RedissonReactiveClusterKeyCommandsTest extends BaseTest {

    @Parameterized.Parameters(name= "{index} - same slot = {0}; has ttl = {1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {false, false},
                {true, false},
                {false, true},
                {true, true}
        });
    }

    @Parameterized.Parameter(0)
    public boolean sameSlot;

    @Parameterized.Parameter(1)
    public boolean hasTtl;

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

    @Test
    public void testRename() {
        testInClusterReactive(connection -> {
            connection.stringCommands().set(originalKey, value).block();

            if (hasTtl) {
                connection.keyCommands().expire(originalKey, Duration.ofSeconds(1000)).block();
            }

            Integer originalSlot = getSlotForKey(originalKey, (RedissonReactiveRedisClusterConnection) connection);
            newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(originalSlot), connection);

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

    @Test
    public void testRename_keyNotExist() {
        testInClusterReactive(connection -> {
            Integer originalSlot = getSlotForKey(originalKey, (RedissonReactiveRedisClusterConnection) connection);
            newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(originalSlot), connection);

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

    @Test
    public void testRenameNX() {
        testInClusterReactive(connection -> {
            connection.stringCommands().set(originalKey, value).block();
            if (hasTtl) {
                connection.keyCommands().expire(originalKey, Duration.ofSeconds(1000)).block();
            }

            Integer originalSlot = getSlotForKey(originalKey, (RedissonReactiveRedisClusterConnection) connection);
            newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(originalSlot), connection);

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

    private Integer getTargetSlot(Integer originalSlot) {
        return sameSlot ? originalSlot : MAX_SLOT - originalSlot - 1;
    }
    
    private Integer getSlotForKey(ByteBuffer key, RedissonReactiveRedisClusterConnection connection) {
        return (Integer) connection.read(null, StringCodec.INSTANCE, RedisCommands.KEYSLOT, key.array()).block();
    }

}
