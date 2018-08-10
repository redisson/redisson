package org.redisson.spring.data.connection;

import org.junit.Before;
import org.redisson.BaseTest;
import org.redisson.RedisRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnection;

import redis.clients.jedis.Jedis;

public abstract class BaseConnectionTest extends BaseTest {

    RedisConnection connection;
    
    @Before
    public void init() {
        Jedis jedis = new Jedis(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        connection = new JedisConnection(jedis);
//        connection = new RedissonConnection(redisson);
    }
    
}
