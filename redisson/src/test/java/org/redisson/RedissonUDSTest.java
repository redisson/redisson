package org.redisson;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonUDSTest {

    @Test
    void test() {
        Assumptions.assumeTrue(System.getenv().get("TEST_SOURCE_PATH") != null);
        String socketPath = System.getenv().getOrDefault("REDIS_SOCKET", "/data/redis.sock");

        Config config = new Config();
        config.setTransportMode(TransportMode.EPOLL);
        config.useSingleServer()
                .setAddress("redis+uds://" + socketPath);

        RedissonClient client = Redisson.create(config);
        client.getBucket("uds-test").set("hello-uds");
        assertThat(client.getBucket("uds-test").get()).isEqualTo("hello-uds");
        client.shutdown();
    }
}