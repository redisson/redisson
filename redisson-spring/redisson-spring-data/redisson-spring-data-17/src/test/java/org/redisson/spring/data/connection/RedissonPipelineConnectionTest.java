package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.Test;
import org.redisson.BaseTest;

public class RedissonPipelineConnectionTest extends BaseConnectionTest {

    @Test
    public void testEcho() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        assertThat(connection.echo("test".getBytes())).isNull();
        assertThat(connection.closePipeline().iterator().next()).isEqualTo("test".getBytes());
    }

    @Test
    public void testSetGet() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        assertThat(connection.isPipelined()).isTrue();
        connection.set("key".getBytes(), "value".getBytes());
        assertThat(connection.get("key".getBytes())).isNull();
        
        List<Object> result = connection.closePipeline();
        assertThat(connection.isPipelined()).isFalse();
        assertThat(result.get(0)).isEqualTo("value".getBytes());
    }

    @Test
    public void testHSetGet() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        assertThat(connection.hSet("key".getBytes(), "field".getBytes(), "value".getBytes())).isNull();
        assertThat(connection.hGet("key".getBytes(), "field".getBytes())).isNull();
        
        List<Object> result = connection.closePipeline();
        assertThat((Boolean)result.get(0)).isTrue();
        assertThat(result.get(1)).isEqualTo("value".getBytes());
    }

    
}
