package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.DefaultStringRedisConnection;
import org.springframework.data.redis.connection.DefaultStringTuple;

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
    public void testLPos() {
        RedissonConnection connection = new RedissonConnection(redisson);
        byte[] key = "lpos".getBytes();
        connection.rPush(key, "a".getBytes(), "b".getBytes(), "c".getBytes(), "b".getBytes());

        assertThat(connection.lPos(key, "b".getBytes(), null, null)).containsExactly(1L);
        assertThat(connection.lPos(key, "x".getBytes(), null, null)).isEmpty();

        connection.openPipeline();
        assertThat(connection.lPos(key, "b".getBytes(), null, null)).isNull();
        connection.lPos(key, "b".getBytes(), -1, null);
        connection.lPos(key, "x".getBytes(), null, null);
        connection.lPos(key, "b".getBytes(), null, 0);
        connection.lPos(key, "x".getBytes(), null, 2);
        assertThat(connection.closePipeline()).containsExactly(Collections.singletonList(1L), Collections.singletonList(3L),
                Collections.emptyList(), Arrays.asList(1L, 3L), Collections.emptyList());
    }

    @Test
    public void testEval() {
        RedissonConnection connection = new RedissonConnection(redisson);
        String sha = connection.scriptingCommands().scriptLoad("return {KEYS[1],ARGV[1]}".getBytes());

        connection.openPipeline();
        assertThat((Object) connection.scriptingCommands().eval("return 1".getBytes(), ReturnType.INTEGER, 0)).isNull();
        connection.closePipeline();

        connection.openPipeline();
        evalEveryReturnType(connection, sha);
        assertEveryReturnType(connection.closePipeline());

        connection.openPipeline();
        connection.multi();
        evalEveryReturnType(connection, sha);
        connection.exec();
        List<Object> pipelined = connection.closePipeline();

        assertThat(pipelined).hasSize(1);
        assertEveryReturnType((List<Object>) pipelined.get(0));

        connection.scriptingCommands().scriptFlush();
        connection.openPipeline();
        connection.scriptingCommands().eval("return KEYS[1]".getBytes(), ReturnType.VALUE, 1, "foo".getBytes());
        connection.multi();
        connection.scriptingCommands().eval("return ARGV[1]".getBytes(), ReturnType.VALUE, 0, "bar".getBytes());
        connection.exec();
        pipelined = connection.closePipeline();

        assertThat(pipelined).hasSize(2);
        assertThat(pipelined.get(0)).isEqualTo("foo".getBytes());
        assertThat((List<Object>) pipelined.get(1)).containsExactly("bar".getBytes());

        new RedissonConnectionFactory(redisson).getReactiveConnection().scriptingCommands().scriptFlush().block();
        connection.openPipeline();
        connection.scriptingCommands().eval("return KEYS[1]".getBytes(), ReturnType.VALUE, 1, "foo".getBytes());
        assertThat(connection.closePipeline()).containsExactly("foo".getBytes());
    }

    private static void evalEveryReturnType(RedissonConnection connection, String sha) {
        connection.scriptingCommands().eval("return KEYS[1]".getBytes(), ReturnType.VALUE, 1, "foo".getBytes());
        connection.scriptingCommands().eval("return redis.call('set','abc','ghk')".getBytes(), ReturnType.STATUS, 0);
        connection.scriptingCommands().eval("return 10".getBytes(), ReturnType.INTEGER, 0);
        connection.scriptingCommands().eval("return false".getBytes(), ReturnType.BOOLEAN, 0);
        connection.scriptingCommands().eval("return {1,2}".getBytes(), ReturnType.MULTI, 0);
        connection.scriptingCommands().evalSha(sha, ReturnType.MULTI, 1, "key1".getBytes(), "arg1".getBytes());
        connection.scriptingCommands().evalSha(sha.getBytes(), ReturnType.MULTI, 1, "key2".getBytes(), "arg2".getBytes());
    }

    private static void assertEveryReturnType(List<Object> results) {
        assertThat(results).hasSize(7);
        assertThat(results.get(0)).isEqualTo("foo".getBytes());
        assertThat(results.get(1)).isEqualTo("OK");
        assertThat(results.get(2)).isEqualTo(10L);
        assertThat(results.get(3)).isEqualTo(false);
        assertThat((List<Object>) results.get(4)).containsExactly(1L, 2L);
        assertThat((List<Object>) results.get(5)).containsExactly("key1".getBytes(), "arg1".getBytes());
        assertThat((List<Object>) results.get(6)).containsExactly("key2".getBytes(), "arg2".getBytes());
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
    public void testTransactionInPipelineIsQueueing() {
        RedissonConnection connection = new RedissonConnection(redisson);
        DefaultStringRedisConnection stringConnection = new DefaultStringRedisConnection(connection);
        stringConnection.setDeserializePipelineAndTxResults(true);
        stringConnection.openPipeline();
        stringConnection.set("a", "1");
        assertThat(connection.isQueueing()).isFalse();

        stringConnection.multi();
        assertThat(connection.isQueueing()).isTrue();
        assertThat(connection.isPipelined()).isTrue();
        stringConnection.set("b", "2");
        stringConnection.get("b");
        stringConnection.zAdd("z", 1, "m");
        stringConnection.zRangeWithScores("z", 0, -1);
        assertThat(stringConnection.exec()).isNull();
        assertThat(connection.isQueueing()).isFalse();

        stringConnection.get("a");
        List<Object> results = stringConnection.closePipeline();

        assertThat(results).hasSize(3);
        assertThat(results.get(0)).isEqualTo(true);
        assertThat((List<Object>) results.get(1)).containsExactly(true, "2", true,
                Collections.singleton(new DefaultStringTuple("m".getBytes(), "m", 1.0)));
        assertThat(results.get(2)).isEqualTo("1");
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
    public void testWatchedTransactionInPipeline() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.watch("key".getBytes());
        connection.openPipeline();

        assertThatThrownBy(connection::multi).isInstanceOf(InvalidDataAccessApiUsageException.class);
        connection.set("key".getBytes(), "value".getBytes());
        assertThat(connection.closePipeline()).containsExactly(true);
        connection.unwatch();
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
