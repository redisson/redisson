package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.redisson.RedisRunner;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.RedisServer;

public class RedissonSentinelConnectionTest {

    RedissonClient redisson;
    RedisSentinelConnection connection;
    RedisRunner.RedisProcess master;
    RedisRunner.RedisProcess slave1;
    RedisRunner.RedisProcess slave2;
    RedisRunner.RedisProcess sentinel1;
    RedisRunner.RedisProcess sentinel2;
    RedisRunner.RedisProcess sentinel3;
    
    @Before
    public void before() throws FailedToStartRedisException, IOException, InterruptedException {
        master = new RedisRunner()
                .nosave()
                .randomDir()
                .run();
        slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        
        Thread.sleep(5000); 
        
        Config config = new Config();
        config.useSentinelServers()
            .setLoadBalancer(new RandomLoadBalancer())
            .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster");
        redisson = Redisson.create(config);
        
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        connection = factory.getSentinelConnection();
    }
    
    @After
    public void after() {
        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();

        redisson.shutdown();
    }
    
    @Test
    public void testMasters() {
        Collection<RedisServer> masters = connection.masters();
        assertThat(masters).hasSize(1);
    }
    
    @Test
    public void testSlaves() {
        Collection<RedisServer> masters = connection.masters();
        Collection<RedisServer> slaves = connection.slaves(masters.iterator().next());
        assertThat(slaves).hasSize(2);
    }

    @Test
    public void testRemove() {
        Collection<RedisServer> masters = connection.masters();
        connection.remove(masters.iterator().next());
    }

    @Test
    public void testMonitor() {
        Collection<RedisServer> masters = connection.masters();
        RedisServer master = masters.iterator().next();
        master.setName(master.getName() + ":");
        connection.monitor(master);
    }
    
    @Test
    public void testFailover() throws InterruptedException {
        Collection<RedisServer> masters = connection.masters();
        connection.failover(masters.iterator().next());
        
        Thread.sleep(10000);
        
        RedisServer newMaster = connection.masters().iterator().next();
        assertThat(masters.iterator().next().getPort()).isNotEqualTo(newMaster.getPort());
    }
}
