package org.redisson.spring.data.connection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.redisson.ClusterRunner;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.Redisson;
import org.redisson.RedissonKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.redisson.reactive.CommandReactiveService;
import org.springframework.data.redis.RedisSystemException;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.redisson.connection.MasterSlaveConnectionManager.MAX_SLOT;

@RunWith(Parameterized.class)
public class RedissonReactiveClusterKeyCommandsTest {

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

    static RedissonClient redisson;
    static RedissonReactiveRedisClusterConnection connection;
    static ClusterProcesses process;

    ByteBuffer originalKey = ByteBuffer.wrap("key".getBytes());
    ByteBuffer newKey = ByteBuffer.wrap("unset".getBytes());
    ByteBuffer value = ByteBuffer.wrap("value".getBytes());

    @BeforeClass
    public static void before() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();


        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
                .setSubscriptionMode(SubscriptionMode.SLAVE)
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());

        redisson = Redisson.create(config);
        connection = new RedissonReactiveRedisClusterConnection(new CommandReactiveService(((RedissonKeys) redisson.getKeys()).getConnectionManager()));
    }

    @AfterClass
    public static void after() {
        process.shutdown();
        redisson.shutdown();
    }

    @After
    public void cleanup() {
        connection.keyCommands().del(originalKey)
                .and(connection.keyCommands().del(newKey))
                .block();
    }

    @Test
    public void testRename() {
        connection.stringCommands().set(originalKey, value).block();

        if (hasTtl) {
            connection.keyCommands().expire(originalKey, Duration.ofSeconds(1000)).block();
        }

        Integer originalSlot = getSlotForKey(originalKey);
        newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(originalSlot));

        Boolean response = connection.keyCommands().rename(originalKey, newKey).block();

        assertThat(response).isTrue();

        final ByteBuffer newKeyValue = connection.stringCommands().get(newKey).block();
        assertThat(newKeyValue).isEqualTo(value);
        if (hasTtl) {
            assertThat(connection.keyCommands().ttl(newKey).block()).isGreaterThan(0);
        } else {
            assertThat(connection.keyCommands().ttl(newKey).block()).isEqualTo(-1);
        }
    }

    @Test
    public void testRename_keyNotExist() {
        Integer originalSlot = getSlotForKey(originalKey);
        newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(originalSlot));

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
    }

    protected ByteBuffer getNewKeyForSlot(String originalKey, Integer targetSlot) {
        int counter = 0;

        ByteBuffer newKey = ByteBuffer.wrap((originalKey + counter).getBytes());

        Integer newKeySlot = getSlotForKey(newKey);

        while(!newKeySlot.equals(targetSlot)) {
            counter++;
            newKey = ByteBuffer.wrap((originalKey + counter).getBytes());
            newKeySlot = getSlotForKey(newKey);
        }

        return newKey;
    }

    @Test
    public void testRenameNX() {
        connection.stringCommands().set(originalKey, value).block();
        if (hasTtl) {
            connection.keyCommands().expire(originalKey, Duration.ofSeconds(1000)).block();
        }

        Integer originalSlot = getSlotForKey(originalKey);
        newKey = getNewKeyForSlot(new String(originalKey.array()), getTargetSlot(originalSlot));

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
    }

    private Integer getTargetSlot(Integer originalSlot) {
        return sameSlot ? originalSlot : MAX_SLOT - originalSlot - 1;
    }
    
    private Integer getSlotForKey(ByteBuffer key) {
        return (Integer) connection.read(null, StringCodec.INSTANCE, RedisCommands.KEYSLOT, key.array()).block();
    }

}
