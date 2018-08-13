package org.redisson.spring.data.connection;

import org.junit.Before;
import org.redisson.BaseTest;
import org.springframework.data.redis.connection.RedisConnection;

public abstract class BaseConnectionTest extends BaseTest {

    RedisConnection connection;
    
    @Before
    public void init() {
        connection = new RedissonConnection(redisson);
    }
    
}
