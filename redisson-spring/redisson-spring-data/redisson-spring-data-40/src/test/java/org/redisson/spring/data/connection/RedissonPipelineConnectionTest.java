package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;

public class RedissonPipelineConnectionTest extends BaseConnectionTest {

    @Test
    public void testDel() {
        RedissonConnection connection = new RedissonConnection(redisson);
        byte[] key = "my_key".getBytes();
        byte[] value = "my_value".getBytes();
        connection.set(key, value);

        connection.openPipeline();
        connection.get(key);
        connection.del(key);

        List<Object> results = connection.closePipeline();
        byte[] val = (byte[])results.get(0);
        assertThat(val).isEqualTo(value);
        Long res = (Long) results.get(1);
        assertThat(res).isEqualTo(1);
    }

    @Test
    public void testEcho() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        assertThat(connection.echo("test".getBytes())).isNull();
        assertThat(connection.closePipeline().iterator().next()).isEqualTo("test".getBytes());
    }

    @Test
    public void testPingKeepsStringReply() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.set("key".getBytes(), "value".getBytes());

        connection.openPipeline();
        connection.ping();
        connection.get("key".getBytes());
        assertThat(connection.closePipeline()).containsExactly("PONG", "value".getBytes());

        connection.setFilterOkResponses(true);
        connection.openPipeline();
        connection.set("key".getBytes(), "value".getBytes());
        connection.ping();
        connection.get("key".getBytes());
        assertThat(connection.closePipeline()).containsExactly("PONG", "value".getBytes());
    }

    @Test
    public void testTransactionInPipeline() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        connection.set("a".getBytes(), "1".getBytes());
        connection.multi();
        connection.set("b".getBytes(), "2".getBytes());
        connection.get("b".getBytes());
        assertThat(connection.exec()).isNull();
        connection.get("a".getBytes());

        List<Object> results = connection.closePipeline();

        assertThat(results).hasSize(3);
        assertThat(results.get(0)).isEqualTo(true);
        assertThat((List<Object>) results.get(1)).containsExactly(true, "2".getBytes());
        assertThat(results.get(2)).isEqualTo("1".getBytes());
    }

    @Test
    public void testDiscardInPipeline() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        connection.set("a".getBytes(), "1".getBytes());
        connection.multi();
        connection.set("b".getBytes(), "2".getBytes());
        connection.discard();
        connection.get("b".getBytes());

        assertThat(connection.closePipeline()).containsExactly(true, null);
        assertThat(connection.get("a".getBytes())).isEqualTo("1".getBytes());
    }

    @Test
    public void testExecute() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.set("key".getBytes(), "value".getBytes());
        connection.openPipeline();
        assertThat(connection.execute("GET", "key".getBytes())).isNull();
        connection.execute("PING");
        connection.execute("SET", "key".getBytes(), "value2".getBytes());

        assertThat(connection.closePipeline()).containsExactly("value".getBytes(), "PONG".getBytes(), "OK".getBytes());
    }

    @Test
    public void testWatchAndUnwatchInPipeline() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        connection.set("key".getBytes(), "value".getBytes());

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertThatThrownBy(() -> connection.watch("key".getBytes()))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class);
            connection.unwatch();
        });
        assertThat(connection.closePipeline()).containsExactly(true);
    }

    @Test
    public void testOpenPipelineTwice() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        connection.set("key".getBytes(), "value".getBytes());
        connection.openPipeline();
        connection.get("key".getBytes());

        assertThat(connection.closePipeline()).containsExactly(true, "value".getBytes());
    }

    @Test
    public void testAllNodeCommandsKeepResultPositions() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.set("key".getBytes(), "value".getBytes());

        connection.openPipeline();
        connection.keys("*".getBytes());
        connection.dbSize();
        connection.randomKey();
        connection.ping();
        List<Object> results = connection.closePipeline();
        assertThat(results).hasSize(4);
        assertThat(results.get(3)).isEqualTo("PONG");

        connection.setFilterOkResponses(true);
        connection.openPipeline();
        connection.dbSize();
        connection.set("key".getBytes(), "value2".getBytes());
        connection.flushAll();
        connection.ping();
        assertThat(connection.closePipeline()).containsExactly(1L, null, "PONG");
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
        assertThat(result.get(1)).isEqualTo("value".getBytes());
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
