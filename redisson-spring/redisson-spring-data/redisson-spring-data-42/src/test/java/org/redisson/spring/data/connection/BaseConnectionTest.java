package org.redisson.spring.data.connection;

import org.junit.jupiter.api.BeforeAll;
import org.redisson.RedisDockerTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

public abstract class BaseConnectionTest extends RedisDockerTest {

    static RedisConnection connection;
    static StringRedisTemplate redisTemplate;
    
    @BeforeAll
    public static void init() {
        connection = new RedissonConnection(redisson);
        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();
    }
    
}
