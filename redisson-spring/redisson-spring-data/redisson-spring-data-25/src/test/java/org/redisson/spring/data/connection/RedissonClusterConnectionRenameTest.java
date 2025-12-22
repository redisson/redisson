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
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.redisson.connection.MasterSlaveConnectionManager.MAX_SLOT;

@RunWith(Parameterized.class)
public class RedissonClusterConnectionRenameTest {

    @Parameterized.Parameters(name= "{index} - same slot = {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {false},
                {true}
        });
    }

    @Parameterized.Parameter(0)
    public boolean sameSlot;

    static RedissonClient redisson;
    static RedissonClusterConnection connection;
    static ClusterProcesses process;

    byte[] originalKey = "key".getBytes();
    byte[] newKey = "unset".getBytes();
    byte[] value = "value".getBytes();

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
        connection = new RedissonClusterConnection(redisson);
    }

    @AfterClass
    public static void after() {
        process.shutdown();
        redisson.shutdown();
    }

    @After
    public void cleanup() {
        connection.del(originalKey);
        connection.del(newKey);
    }

    @Test
    public void testRename() {
        connection.set(originalKey, value);
        connection.expire(originalKey, 1000);

        Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
        newKey = getNewKeyForSlot(originalKey, getTargetSlot(originalSlot));

        connection.rename(originalKey, newKey);

        assertThat(connection.get(newKey)).isEqualTo(value);
        assertThat(connection.ttl(newKey)).isGreaterThan(0);
    }

    @Test
    public void testRename_pipeline() {
        connection.set(originalKey, value);

        Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
        newKey = getNewKeyForSlot(originalKey, getTargetSlot(originalSlot));

        connection.openPipeline();
        assertThatThrownBy(() -> connection.rename(originalKey, newKey)).isInstanceOf(InvalidDataAccessResourceUsageException.class);
        connection.closePipeline();
    }

    protected byte[] getNewKeyForSlot(byte[] originalKey, Integer targetSlot) {
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
        connection.set(originalKey, value);
        connection.expire(originalKey, 1000);

        Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
        newKey = getNewKeyForSlot(originalKey, getTargetSlot(originalSlot));

        Boolean result = connection.renameNX(originalKey, newKey);

        assertThat(connection.get(newKey)).isEqualTo(value);
        assertThat(connection.ttl(newKey)).isGreaterThan(0);
        assertThat(result).isTrue();

        connection.set(originalKey, value);

        result = connection.renameNX(originalKey, newKey);

        assertThat(result).isFalse();
    }

    @Test
    public void testRenameNX_pipeline() {
        connection.set(originalKey, value);

        Integer originalSlot = connection.clusterGetSlotForKey(originalKey);
        newKey = getNewKeyForSlot(originalKey, getTargetSlot(originalSlot));

        connection.openPipeline();
        assertThatThrownBy(() -> connection.renameNX(originalKey, newKey)).isInstanceOf(InvalidDataAccessResourceUsageException.class);
        connection.closePipeline();
    }

    private Integer getTargetSlot(Integer originalSlot) {
        return sameSlot ? originalSlot : MAX_SLOT - originalSlot - 1;
    }

}
