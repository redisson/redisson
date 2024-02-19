package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.NatMapper;
import org.redisson.api.RShardedTopic;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonShardedTopicTest extends RedisDockerTest {

    @Test
    public void testInvalidCommand() {
        GenericContainer<?> redis = createRedis("6.2");
        redis.start();

        Config config = new Config();
        config.setProtocol(protocol);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);

        RShardedTopic t = redisson.getShardedTopic("ttt");
        RedisException e = Assertions.assertThrows(RedisException.class, () -> {
            t.addListener(String.class, (channel, msg) -> {
            });
        });
        assertThat(e.getMessage()).contains("ERR unknown command `SSUBSCRIBE`");

        redisson.shutdown();
        redis.stop();
    }

    @Test
    public void testClusterSharding() {
        testInCluster(redisson -> {
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
        });
    }


}
