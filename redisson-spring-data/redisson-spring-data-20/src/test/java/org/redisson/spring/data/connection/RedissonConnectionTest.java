package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class RedissonConnectionTest extends BaseConnectionTest {
    
    @Test
    public void testEcho() {
        assertThat(connection.echo("test".getBytes())).isEqualTo("test".getBytes());
    }

    @Test
    public void testSetGet() {
        connection.set("key".getBytes(), "value".getBytes());
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testHSetGet() {
        assertThat(connection.hSet("key".getBytes(), "field".getBytes(), "value".getBytes())).isTrue();
        assertThat(connection.hGet("key".getBytes(), "field".getBytes())).isEqualTo("value".getBytes());
    }
    
}
