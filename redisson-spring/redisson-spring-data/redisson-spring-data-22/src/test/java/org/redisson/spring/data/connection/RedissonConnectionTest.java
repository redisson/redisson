package org.redisson.spring.data.connection;

import org.junit.Test;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonConnectionTest extends BaseConnectionTest {

    @Test
    public void testSetExpiration2() {
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(10), SetOption.SET_IF_ABSENT)).isTrue();
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(10), SetOption.SET_IF_ABSENT)).isFalse();
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(10), SetOption.SET_IF_ABSENT)).isFalse();
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }

    @Test
    public void testZSet() {
        connection.zAdd(new byte[] {1}, -1, new byte[] {1});
        connection.zAdd(new byte[] {1}, 2, new byte[] {2});
        connection.zAdd(new byte[] {1}, 10, new byte[] {3});

        assertThat(connection.zRangeByScore(new byte[] {1}, Double.NEGATIVE_INFINITY, 5))
                .containsOnly(new byte[] {1}, new byte[] {2});
    }

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
    public void testSetExpiration() {
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(111122), SetOption.SET_IF_ABSENT)).isTrue();
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testHSetGet() {
        assertThat(connection.hSet("key".getBytes(), "field".getBytes(), "value".getBytes())).isTrue();
        assertThat(connection.hGet("key".getBytes(), "field".getBytes())).isEqualTo("value".getBytes());
    }

    @Test
    public void testZScan() {
        connection.zAdd("key".getBytes(), 1, "value1".getBytes());
        connection.zAdd("key".getBytes(), 2, "value2".getBytes());

        Cursor<RedisZSetCommands.Tuple> t = connection.zScan("key".getBytes(), ScanOptions.scanOptions().build());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value1".getBytes());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value2".getBytes());
    }

    @Test
    public void testGetClientList() {
        List<RedisClientInfo> info = connection.getClientList();
        assertThat(info.size()).isGreaterThan(10);
    }

    @Test
    public void testPTtl() {
        connection.set("key1".getBytes(), "value1".getBytes());
        connection.set("key2".getBytes(), "value2".getBytes());
        connection.expire("key1".getBytes(), 10);

        assertThat(connection.pTtl("key1".getBytes(), TimeUnit.MILLISECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(10000L);
        assertThat(connection.pTtl("key1".getBytes(), TimeUnit.SECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(10L);
        assertThat(connection.pTtl("key1".getBytes(), TimeUnit.MINUTES)).isEqualTo(0L);
        assertThat(connection.pTtl("key1".getBytes())).isGreaterThan(0L).isLessThanOrEqualTo(10000L);

        assertThat(connection.pTtl("key2".getBytes(), TimeUnit.SECONDS)).isEqualTo(-1L);
        assertThat(connection.pTtl("key2".getBytes(), TimeUnit.MINUTES)).isEqualTo(-1L);
        assertThat(connection.pTtl("key2".getBytes())).isEqualTo(-1L);

        assertThat(connection.pTtl("key3".getBytes(), TimeUnit.SECONDS)).isEqualTo(-2L);
        assertThat(connection.pTtl("key3".getBytes(), TimeUnit.MINUTES)).isEqualTo(-2L);
        assertThat(connection.pTtl("key3".getBytes())).isEqualTo(-2L);
    }

    @Test
    public void testTtl() {
        connection.set("key1".getBytes(), "value1".getBytes());
        connection.set("key2".getBytes(), "value2".getBytes());
        connection.expire("key1".getBytes(), 10);

        assertThat(connection.ttl("key1".getBytes(), TimeUnit.MILLISECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(10000L);
        assertThat(connection.ttl("key1".getBytes(), TimeUnit.SECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(10L);
        assertThat(connection.ttl("key1".getBytes(), TimeUnit.MINUTES)).isEqualTo(0L);
        assertThat(connection.ttl("key1".getBytes())).isGreaterThan(0L).isLessThanOrEqualTo(10000L);

        assertThat(connection.ttl("key2".getBytes(), TimeUnit.SECONDS)).isEqualTo(-1L);
        assertThat(connection.ttl("key2".getBytes(), TimeUnit.MINUTES)).isEqualTo(-1L);
        assertThat(connection.ttl("key2".getBytes())).isEqualTo(-1L);

        assertThat(connection.ttl("key3".getBytes(), TimeUnit.SECONDS)).isEqualTo(-2L);
        assertThat(connection.ttl("key3".getBytes(), TimeUnit.MINUTES)).isEqualTo(-2L);
        assertThat(connection.ttl("key3".getBytes())).isEqualTo(-2L);
    }

}
