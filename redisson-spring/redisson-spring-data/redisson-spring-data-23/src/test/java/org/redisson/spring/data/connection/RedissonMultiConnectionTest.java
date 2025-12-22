package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.redisson.BaseTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

public class RedissonMultiConnectionTest extends BaseConnectionTest {

    @Test
    public void testBroken() throws InterruptedException {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(new RedissonConnectionFactory(redisson));
        ExecutorService e = Executors.newFixedThreadPool(32);
        AtomicBoolean hasErrors = new AtomicBoolean();
        for (int i = 0; i < 10; i++) {
            e.submit(() -> {
                stringRedisTemplate.execute(new SessionCallback<Void>() {
                    @Override
                    public Void execute(RedisOperations operations) throws DataAccessException {
                        try {
                            ValueOperations<String, String> valueOps = operations.opsForValue();
                            operations.multi();
                            valueOps.set("test3", "value");
                        } catch (Exception e) {
                            e.printStackTrace();
                            hasErrors.set(true);
                        }
                        return null;
                    }
                });
                stringRedisTemplate.execute(new SessionCallback<Void>() {
                    @Override
                    public Void execute(RedisOperations operations) throws DataAccessException {
                        try {
                            ValueOperations<String, String> valueOps = operations.opsForValue();
                            valueOps.set("test1", "value");
                            assertThat(valueOps.get("test1")).isEqualTo("value");
                        } catch (Exception e) {
                            e.printStackTrace();
                            hasErrors.set(true);
                        }
                        return null;
                    }
                });
            });
        }
        e.shutdown();
        e.awaitTermination(1, TimeUnit.MINUTES);
        assertThat(hasErrors).isFalse();
    }

    @Test
    public void testEcho() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.multi();
        assertThat(connection.echo("test".getBytes())).isNull();
        assertThat(connection.exec().iterator().next()).isEqualTo("test".getBytes());
    }

    @Test
    public void testSetGet() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.multi();
        assertThat(connection.isQueueing()).isTrue();
        connection.set("key".getBytes(), "value".getBytes());
        assertThat(connection.get("key".getBytes())).isNull();
        
        List<Object> result = connection.exec();
        assertThat(connection.isQueueing()).isFalse();
        assertThat(result.get(0)).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testHSetGet() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.multi();
        assertThat(connection.hSet("key".getBytes(), "field".getBytes(), "value".getBytes())).isNull();
        assertThat(connection.hGet("key".getBytes(), "field".getBytes())).isNull();
        
        List<Object> result = connection.exec();
        assertThat((Boolean)result.get(0)).isTrue();
        assertThat(result.get(1)).isEqualTo("value".getBytes());
    }
    
}
