package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.HostPortNatMapper;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class RedissonShardedTopicTest {

    @Container
    private final FixedHostPortGenericContainer<?> redisClusterContainer =
            new FixedHostPortGenericContainer<>("vishnunair/docker-redis-cluster")
                .withFixedExposedPort(5000, 6379)
                .withFixedExposedPort(5001, 6380)
                .withFixedExposedPort(5002, 6381)
                .withFixedExposedPort(5003, 6382)
                .withFixedExposedPort(5004, 6383)
                .withFixedExposedPort(5005, 6384)
                .withStartupCheckStrategy(
                        new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(6))
                );
    @Test
    public void testClusterSharding() {
        Config config = new Config();
        HostPortNatMapper m = new HostPortNatMapper();
        Map<String, String> mm = new HashMap<>();

        String ip = redisClusterContainer.getCurrentContainerInfo().getNetworkSettings().getIpAddress();
        mm.put(ip + ":6380", "127.0.0.1:5001");
        mm.put(ip + ":6382", "127.0.0.1:5003");
        mm.put(ip + ":6379", "127.0.0.1:5000");
        mm.put(ip + ":6383", "127.0.0.1:5004");
        mm.put(ip + ":6384", "127.0.0.1:5005");
        mm.put(ip + ":6381", "127.0.0.1:5002");
        m.setHostsPortMap(mm);
        config.useClusterServers()
                .setPingConnectionInterval(0)
                .setNatMapper(m)
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress("redis://127.0.0.1:5000");
        RedissonClient redisson = Redisson.create(config);

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

        redisson.shutdown();

    }


}
