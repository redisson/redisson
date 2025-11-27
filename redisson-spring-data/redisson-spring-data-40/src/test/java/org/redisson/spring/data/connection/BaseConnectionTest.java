package org.redisson.spring.data.connection;

import org.junit.Before;
import org.redisson.BaseTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

public abstract class BaseConnectionTest extends BaseTest {

    RedisConnection connection;
    StringRedisTemplate redisTemplate;
    
    @Before
    public void init() {
        connection = new RedissonConnection(redisson);
        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();
    }
    
}
