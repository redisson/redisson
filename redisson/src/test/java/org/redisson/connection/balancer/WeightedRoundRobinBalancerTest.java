package org.redisson.connection.balancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.testcontainers.containers.GenericContainer;

public class WeightedRoundRobinBalancerTest extends RedisDockerTest {

    @Test
    public void testUseMasterForReadsIfNoConnectionsToSlaves() {
            GenericContainer<?> master = null;
            GenericContainer<?> slave = null;
            RedissonClient client = null;
            try {
                master = createRedis();
                master.start();
                slave = createRedis();
                slave.start();

                String masterurl = "redis://" + master.getHost() + ":" + master.getFirstMappedPort();
                String slaveurl = "redis://" + slave.getHost() + ":" + slave.getFirstMappedPort();

                Map<String, Integer> weights = new HashMap<>();
                weights.put(masterurl, 1);
                weights.put(slaveurl, 2);

                Config config = new Config();
                config.useMasterSlaveServers()
                        .setReadMode(ReadMode.SLAVE)
                        .setMasterAddress(masterurl)
                        .addSlaveAddress(slaveurl)
                        .setLoadBalancer(new WeightedRoundRobinBalancer(weights, 1));

                client = Redisson.create(config);

                // To simulate network connection issues to slave, stop the slave
                // after creating the client. Cannot create the client without the
                // slave running. See https://github.com/mrniko/redisson/issues/580
                slave.stop();

                RedissonClient clientCopy = client;
                Assertions.assertThrows(WriteRedisConnectionException.class, () -> {
                    clientCopy.getBucket("key").get();
                });
            } finally {
                if (master != null) {
                    master.stop();
                }
                if (slave != null) {
                    slave.stop();
                }
                if (client != null) {
                    client.shutdown();
                }
            }
    }

}
