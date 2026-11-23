package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedissonMultiConnectionTest extends BaseConnectionTest {

    @Test
    public void testPingKeepsStringReply() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.set("key".getBytes(), "value".getBytes());

        connection.multi();
        connection.ping();
        connection.get("key".getBytes());
        assertThat(connection.exec()).containsExactly("PONG", "value".getBytes());
    }

    @Test
    public void testEval() {
        RedissonConnection connection = new RedissonConnection(redisson);
        String sha = connection.scriptLoad("return {KEYS[1],ARGV[1]}".getBytes());

        connection.multi();
        connection.eval("return KEYS[1]".getBytes(), ReturnType.VALUE, 1, "foo".getBytes());
        connection.eval("return redis.call('set','abc','ghk')".getBytes(), ReturnType.STATUS, 0);
        connection.eval("return 10".getBytes(), ReturnType.INTEGER, 0);
        connection.eval("return false".getBytes(), ReturnType.BOOLEAN, 0);
        connection.eval("return {1,2}".getBytes(), ReturnType.MULTI, 0);
        connection.evalSha(sha, ReturnType.MULTI, 1, "key1".getBytes(), "arg1".getBytes());
        connection.evalSha(sha.getBytes(), ReturnType.MULTI, 1, "key2".getBytes(), "arg2".getBytes());
        List<Object> results = connection.exec();

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
    public void testFlushAll() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.set("key".getBytes(), "value".getBytes());

        connection.multi();
        connection.set("key2".getBytes(), "value".getBytes());
        connection.flushAll();
        List<Object> results = connection.exec();

        assertThat(results).hasSize(1);
        assertThat(connection.isQueueing()).isFalse();
        assertThat(connection.exists("key".getBytes())).isFalse();
        assertThat(connection.exists("key2".getBytes())).isFalse();
    }

    @Test
    public void testCommandRejectedWhileQueueing() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.multi();
        connection.set("key".getBytes(), "value".getBytes());

        assertThatThrownBy(() -> connection.save())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("not allowed inside a transaction");
        assertThatThrownBy(connection::exec)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Transaction discarded")
                .rootCause().hasMessageStartingWith("ERR Command not allowed inside a transaction");

        assertThat(connection.isQueueing()).isFalse();
        assertThat(connection.get("key".getBytes())).isNull();

        connection.multi();
        connection.set("key".getBytes(), "value".getBytes());
        assertThatThrownBy(() -> connection.save()).isInstanceOf(DataAccessException.class);
        connection.discard();
        assertThat(connection.isQueueing()).isFalse();
        assertThat(connection.get("key".getBytes())).isNull();
    }

    @Test
    public void testExecute() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.execute("MULTI");
        assertThat(connection.isQueueing()).isTrue();
        assertThat(connection.execute("SET", "key".getBytes(), "1".getBytes())).isNull();
        assertThat(connection.execute("INCR", "key".getBytes())).isNull();
        assertThat(connection.execute("TYPE", "key".getBytes())).isNull();

        assertThat((List<Object>) connection.execute("EXEC")).containsExactly(2L, "string".getBytes());
        assertThat(connection.isQueueing()).isFalse();
    }

    @Test
    public void testWatchUsesTheConnectionOfTheTransaction() throws InterruptedException {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(new RedissonConnectionFactory(redisson));
        ExecutorService load = Executors.newFixedThreadPool(6);
        AtomicBoolean stop = new AtomicBoolean();
        for (int i = 0; i < 6; i++) {
            load.submit(() -> {
                while (!stop.get()) {
                    redisTemplate.opsForValue().get("load");
                }
            });
        }
        RedissonConnection connection = new RedissonConnection(redisson);
        RedissonConnection other = new RedissonConnection(redisson);
        byte[] key = "watched".getBytes();
        try {
            for (int i = 0; i < 40; i++) {
                connection.set(key, "0".getBytes());
                connection.watch(key);
                other.set(key, "changed".getBytes());
                connection.multi();
                connection.set(key, "mine".getBytes());
                connection.exec();

                assertThat(other.get(key)).isEqualTo("changed".getBytes());
            }
        } finally {
            stop.set(true);
            load.shutdown();
            load.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @EnumSource(Protocol.class)
    public void testExecOfAbortedTransactionReturnsNull(Protocol protocol) {
        Config config = createConfig();
        config.setProtocol(protocol);
        RedissonClient client = Redisson.create(config);
        try {
            RedissonConnection connection = new RedissonConnection(client);
            RedissonConnection other = new RedissonConnection(client);
            byte[] key = "watched".getBytes();

            connection.watch(key);
            other.set(key, "changed".getBytes());
            connection.multi();
            connection.set(key, "mine".getBytes());
            assertThat(connection.exec()).isNull();
            assertThat(other.get(key)).isEqualTo("changed".getBytes());

            connection.watch(key);
            other.set(key, "again".getBytes());
            connection.multi();
            assertThat(connection.exec()).isNull();

            connection.watch(key);
            connection.multi();
            assertThat(connection.exec()).isEmpty();

            connection.watch(key);
            connection.multi();
            connection.set(key, "mine".getBytes());
            assertThat(connection.exec()).isEmpty();
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testSessionCallbackSeesAbortedTransaction() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(new RedissonConnectionFactory(redisson));
        StringRedisTemplate template = new StringRedisTemplate(new RedissonConnectionFactory(redisson));
        List<Object> results = template.execute(new SessionCallback<List<Object>>() {
            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.watch("watched");
                redisTemplate.opsForValue().set("watched", "changed");
                operations.multi();
                operations.opsForValue().set("watched", "mine");
                operations.opsForValue().get("watched");
                return operations.exec();
            }
        });

        assertThat(results).isNull();
        assertThat(redisTemplate.opsForValue().get("watched")).isEqualTo("changed");
    }

    @Test
    public void testWatchedTransactionReturnsItsConnection() {
        RedissonConnection connection = new RedissonConnection(redisson);
        byte[] key = "watched".getBytes();
        for (int i = 0; i < 100; i++) {
            connection.watch(key);
            connection.multi();
            assertThat(connection.exec()).isEmpty();

            connection.watch(key);
            connection.multi();
            connection.set(key, "value".getBytes());
            connection.discard();

            connection.watch(key);
            connection.multi();
            connection.discard();
        }
        assertThat(connection.get(key)).isNull();
    }

    @Test
    public void testUnwatchAndCloseLeaveNoWatchBehind() {
        RedissonConnection connection = new RedissonConnection(redisson);
        RedissonConnection other = new RedissonConnection(redisson);
        byte[] key = "watched".getBytes();

        connection.watch(key);
        connection.unwatch();
        other.set(key, "changed".getBytes());
        connection.multi();
        connection.set(key, "mine".getBytes());
        assertThat(connection.exec()).isEmpty();
        assertThat(other.get(key)).isEqualTo("mine".getBytes());

        connection.watch(key);
        connection.close();
        other.set(key, "again".getBytes());
        for (int i = 0; i < 20; i++) {
            RedissonConnection next = new RedissonConnection(redisson);
            next.multi();
            next.set(key, ("next" + i).getBytes());
            next.get(key);
            assertThat(next.exec()).containsExactly(("next" + i).getBytes());
        }
    }

    @Test
    public void testWatchAndUnwatchInMulti() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.multi();
        connection.set("key".getBytes(), "value".getBytes());

        assertThatThrownBy(() -> connection.watch("key".getBytes()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertTimeoutPreemptively(Duration.ofSeconds(10), connection::unwatch);
        assertThat(connection.exec()).isEmpty();
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
