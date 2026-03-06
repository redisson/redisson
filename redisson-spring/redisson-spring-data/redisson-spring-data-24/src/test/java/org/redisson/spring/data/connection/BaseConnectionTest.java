package org.redisson.spring.data.connection;

import org.junit.jupiter.api.BeforeAll;
import org.redisson.RedisDockerTest;
import org.springframework.data.redis.connection.RedisConnection;

public abstract class BaseConnectionTest extends RedisDockerTest {

    static RedisConnection connection;
    
    @BeforeAll
    public static void init() {
        connection = new RedissonConnection(redisson);
    }
    
}
