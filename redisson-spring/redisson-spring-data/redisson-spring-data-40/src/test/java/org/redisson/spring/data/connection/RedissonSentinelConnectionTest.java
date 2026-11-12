package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.RedisServer;

import java.util.Collection;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSentinelConnectionTest extends RedisDockerTest {

    void withSentinel(Consumer<RedisSentinelConnection> callback) throws InterruptedException {
        withSentinel((containers, c) -> {
            RedissonClient redisson = Redisson.create(c);

            RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
            RedisSentinelConnection connection = factory.getSentinelConnection();
            callback.accept(connection);
        }, 2);
    }

    @Test
    public void testSentinelConnectionOfConnection() throws InterruptedException {
        withSentinel((containers, config) -> {
            RedissonClient redisson = Redisson.create(config);
            try {
                RedisConnection connection = new RedissonConnectionFactory(redisson).getConnection();
                RedisSentinelConnection sentinel = connection.getSentinelConnection();
                assertThat(sentinel.masters()).hasSize(1);
                assertThat(connection.getSentinelConnection()).isSameAs(sentinel);

                connection.close();
                assertThat(sentinel.isOpen()).isFalse();
            } finally {
                redisson.shutdown();
            }
        }, 2);
    }

    @Test
    public void testMasters() throws InterruptedException {
        withSentinel(connection -> {
            Collection<RedisServer> masters = connection.masters();
            assertThat(masters).hasSize(1);
        });
    }
    
    @Test
    public void testSlaves() throws InterruptedException {
        withSentinel(connection -> {
            Collection<RedisServer> masters = connection.masters();
            Collection<RedisServer> slaves = connection.replicas(masters.iterator().next());
            assertThat(slaves).hasSize(2);
        });
    }

    @Test
    public void testRemove() throws InterruptedException {
        withSentinel(connection -> {
            Collection<RedisServer> masters = connection.masters();
            connection.remove(masters.iterator().next());
        });
    }

    @Test
    public void testMonitor() throws InterruptedException {
        withSentinel(connection -> {
            Collection<RedisServer> masters = connection.masters();
            RedisServer master = masters.iterator().next();
            master.setName(master.getName() + ":");
            connection.monitor(master);
        });
    }
    
    @Test
    public void testFailover() throws InterruptedException {
        withSentinel(connection -> {
            Collection<RedisServer> masters = connection.masters();
            connection.failover(masters.iterator().next());

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            RedisServer newMaster = connection.masters().iterator().next();
            assertThat(masters.iterator().next().getPort()).isNotEqualTo(newMaster.getPort());
        });
    }
}
