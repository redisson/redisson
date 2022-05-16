package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.HostPortNatMapper;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonShardedTopicTest {

    @Test
    public void testClusterSharding() {
        Config config = new Config();
        HostPortNatMapper m = new HostPortNatMapper();
        Map<String, String> mm = new HashMap<>();
        mm.put("172.17.0.2:6380", "127.0.0.1:5001");
        mm.put("172.17.0.2:6382", "127.0.0.1:5003");
        mm.put("172.17.0.2:6379", "127.0.0.1:5000");
        mm.put("172.17.0.2:6383", "127.0.0.1:5004");
        mm.put("172.17.0.2:6384", "127.0.0.1:5005");
        mm.put("172.17.0.2:6381", "127.0.0.1:5002");
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
