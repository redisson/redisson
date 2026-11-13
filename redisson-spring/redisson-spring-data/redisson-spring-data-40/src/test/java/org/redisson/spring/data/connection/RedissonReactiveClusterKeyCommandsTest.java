package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.*;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.ReactiveKeyCommands.RenameCommand;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.RedisClusterNode;
import reactor.core.publisher.Flux;


import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
            Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                assertThat(connection.keyCommands().exists(originalKey).block()).isFalse();
            });
            if (hasTtl) {
                assertThat(connection.keyCommands().ttl(newKey).block()).isGreaterThan(0);
            } else {
                assertThat(connection.keyCommands().ttl(newKey).block()).isEqualTo(-1);
            }
        });
    }

    @Test
    public void testClusterGetKeysInSlotOfEveryMaster() {
        testInClusterReactive(connection -> {
            for (int i = 0; i < 20; i++) {
                ByteBuffer key = ByteBuffer.wrap(("key" + i).getBytes());
                connection.stringCommands().set(key, value).block();
                Integer slot = connection.clusterGetSlotForKey(key).block();

                assertThat(connection.clusterGetKeysInSlot(slot, 10).collectList().block()).containsExactly(key);
            }
        });
    }

    @Test
    public void testMUnlink() {
        testInClusterReactive(connection -> {
            connection.stringCommands().set(ByteBuffer.wrap("key{1}".getBytes()), value).block();
            connection.stringCommands().set(ByteBuffer.wrap("key{16383}".getBytes()), value).block();

            List<ByteBuffer> keys = connection.keyCommands().keys(ByteBuffer.wrap("key*".getBytes())).block();
            Long res = connection.keyCommands().mUnlink(keys).block();
            assertThat(res).isEqualTo(2);
        });
    }

    @Test
    public void testKeysKeepsBinaryKeys() {
        testInClusterReactive(connection -> {
            ByteBuffer key = ByteBuffer.wrap(new byte[] {'b', (byte) 0xFF, (byte) 0xFE, 'k'});
            connection.stringCommands().set(key, value).block();
            RedisClusterNode node = connection.clusterGetNodeForKey(key).block();

            Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                assertThat(connection.keyCommands().keys(ByteBuffer.wrap("b*".getBytes())).block()).containsExactly(key);
                assertThat(connection.keyCommands().keys(node, ByteBuffer.wrap("b*".getBytes())).block()).containsExactly(key);
            });
        });
    }

    @Test
    public void testKeysOnNodeReturnsOnlyKeysOfThatNode() {
        testInClusterReactive(connection -> {
            List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap("a".getBytes()), ByteBuffer.wrap("b".getBytes()),
                    ByteBuffer.wrap("c".getBytes()));
            keys.forEach(key -> connection.stringCommands().set(key, value).block());
            RedisClusterNode node = connection.clusterGetNodeForKey(keys.get(0)).block();
            List<ByteBuffer> nodeKeys = keys.stream()
                    .filter(key -> node.getSlotRange().contains(connection.clusterGetSlotForKey(key).block()))
                    .collect(Collectors.toList());
            assertThat(nodeKeys).hasSizeLessThan(keys.size());

            assertThat(connection.keyCommands().keys(node, ByteBuffer.wrap("*".getBytes())).block())
                    .containsExactlyInAnyOrderElementsOf(nodeKeys);
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRename_keyNotExist(boolean sameSlot, boolean hasTtl) {
        testInClusterReactive(connection -> {
            Integer originalSlot = getSlotForKey(originalKey, (RedissonReactiveRedisClusterConnection) connection);
            newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(sameSlot, originalSlot), connection);

            assertThatThrownBy(() -> connection.keyCommands().rename(originalKey, newKey).block())
                    .isInstanceOf(RedisSystemException.class)
                    .hasMessageContaining("no such key");
            assertThatThrownBy(() -> connection.keyCommands().renameNX(originalKey, newKey).block())
                    .isInstanceOf(RedisSystemException.class)
                    .hasMessageContaining("no such key");
            assertThat(connection.keyCommands().exists(newKey).block()).isFalse();
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRenameOverwritesNewKey(boolean sameSlot, boolean hasTtl) {
        testInClusterReactive(connection -> {
            connection.stringCommands().set(originalKey, value).block();
            Integer originalSlot = getSlotForKey(originalKey, (RedissonReactiveRedisClusterConnection) connection);
            newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(sameSlot, originalSlot), connection);
            connection.stringCommands().set(newKey, ByteBuffer.wrap("existing".getBytes())).block();
            Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                assertThat(connection.keyCommands().exists(newKey).block()).isTrue();
            });

            assertThat(connection.keyCommands().renameNX(originalKey, newKey).block()).isFalse();
            assertThat(connection.keyCommands().rename(originalKey, newKey).block()).isTrue();

            Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                assertThat(connection.stringCommands().get(newKey).block()).isEqualTo(value);
                assertThat(connection.keyCommands().exists(originalKey).block()).isFalse();
            });
        });
    }

    @Test
    public void testRenameOfSeveralCommands() {
        testInClusterReactive(connection -> {
            List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap("{a}1".getBytes()), ByteBuffer.wrap("{a}2".getBytes()),
                    ByteBuffer.wrap("{b}1".getBytes()), ByteBuffer.wrap("{b}2".getBytes()));
            connection.stringCommands().set(keys.get(0), value).block();
            connection.stringCommands().set(keys.get(2), value).block();

            List<Boolean> responses = connection.keyCommands().rename(Flux.just(RenameCommand.key(keys.get(0)).to(keys.get(1)),
                            RenameCommand.key(keys.get(2)).to(keys.get(3))))
                    .map(BooleanResponse::getOutput).collectList().block();
            assertThat(responses).containsExactly(true, true);

            List<Boolean> nxResponses = connection.keyCommands().renameNX(Flux.just(RenameCommand.key(keys.get(1)).to(keys.get(0)),
                            RenameCommand.key(keys.get(3)).to(keys.get(2))))
                    .map(BooleanResponse::getOutput).collectList().block();
            assertThat(nxResponses).containsExactly(true, true);
            Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                assertThat(connection.keyCommands().exists(keys).block()).isEqualTo(2);
                assertThat(connection.stringCommands().get(keys.get(0)).block()).isEqualTo(value);
                assertThat(connection.stringCommands().get(keys.get(2)).block()).isEqualTo(value);
            });
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
            Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                assertThat(connection.keyCommands().exists(originalKey).block()).isFalse();
            });
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
