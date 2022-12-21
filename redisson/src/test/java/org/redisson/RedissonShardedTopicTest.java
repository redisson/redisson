package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonShardedTopicTest {
    static RedissonClient redisson;
    static ClusterRunner.ClusterProcesses process;

    @BeforeAll
    public static void before()
            throws RedisRunner.FailedToStartRedisException, IOException, InterruptedException {
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
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress(
                        process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());

        redisson = Redisson.create(config);
    }

    @AfterAll
    public static void after() {
        process.shutdown();
        redisson.shutdown();
    }

    @Test
    public void testClusterSharding() {
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            int j = i;
            RTopic topic = redisson.getShardedTopic("test" + i);
            topic.addListener(Integer.class, (c, v) -> {
                assertThat(v).isEqualTo(j);
                counter.incrementAndGet();
            });
        }

        for (int i = 0; i < 10; i++) {
            RTopic topic = redisson.getShardedTopic("test" + i);
            long s = topic.publish(i);
            assertThat(s).isEqualTo(1);
        }

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> counter.get() == 10);

        for (int i = 0; i < 10; i++) {
            RTopic topic = redisson.getShardedTopic("test" + i);
            topic.removeAllListeners();
        }
    }
}
