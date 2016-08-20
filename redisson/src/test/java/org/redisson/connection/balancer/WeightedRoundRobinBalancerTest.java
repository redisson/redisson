package org.redisson.connection.balancer;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.redisson.RedisRunner;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;

public class WeightedRoundRobinBalancerTest {
	
    @Test
	public void testUseMasterForReadsIfNoConnectionsToSlaves() throws IOException, InterruptedException {
        RedisProcess master = null;
        RedisProcess slave = null;
        RedissonClient client = null;
        try {
        	master = redisTestInstance(6379);
        	slave = redisTestInstance(6380);
        	
        	Map<String, Integer> weights = new HashMap<>();
        	weights.put("127.0.0.1:6379", 1);
        	weights.put("127.0.0.1:6380", 2);
        	
        	Config config = new Config();
        	config.useMasterSlaveServers()
	        	.setReadMode(ReadMode.SLAVE)
	        	.setMasterAddress("127.0.0.1:6379")
	        	.addSlaveAddress("127.0.0.1:6380")
	        	.setLoadBalancer(new WeightedRoundRobinBalancer(weights, 1));
        	
			client = Redisson.create(config);
        	
			// To simulate network connection issues to slave, stop the slave
			// after creating the client. Cannot create the client without the
			// slave running. See https://github.com/mrniko/redisson/issues/580
        	slave.stop();
        	
        	RedissonClient clientCopy = client;
        	await().atMost(5, TimeUnit.SECONDS).until(() -> assertThat(clientCopy.getBucket("key").get()).isNull());
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
    
	private RedisProcess redisTestInstance(int port) throws IOException, InterruptedException {
        return new RedisRunner()
                .nosave()
                .randomDir()
                .port(port)
                .run();
    }
}
