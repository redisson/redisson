package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.ExpirationOptions;
import org.springframework.data.redis.connection.ReactiveKeyCommands;
import org.springframework.data.redis.connection.ReactiveKeyCommands.ExpireAtCommand;
import org.springframework.data.redis.connection.ReactiveKeyCommands.ExpireCommand;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveKeyCommandsTest extends BaseConnectionTest {

    @Test
    public void testExpiration() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveStringRedisTemplate t = new ReactiveStringRedisTemplate(factory);
        t.opsForValue().set("123", "4343").block();
        t.expire("123", Duration.ofMillis(1001)).block();
        assertThat(t.getExpire("123").block().toMillis()).isBetween(900L, 1000L);
    }

    @Test
    public void testExpireKeepsMilliseconds() {
        ReactiveKeyCommands keyCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().keyCommands();
        connection.stringCommands().set("expire-500ms".getBytes(), "value".getBytes());
        connection.stringCommands().set("expire-1500ms".getBytes(), "value".getBytes());

        assertThat(keyCommands.expire(ByteBuffer.wrap("expire-500ms".getBytes()), Duration.ofMillis(500)).block()).isTrue();
        assertThat(connection.keyCommands().pTtl("expire-500ms".getBytes())).isBetween(1L, 500L);
        assertThat(keyCommands.expire(ByteBuffer.wrap("expire-1500ms".getBytes()), Duration.ofMillis(1500)).block()).isTrue();
        assertThat(connection.keyCommands().pTtl("expire-1500ms".getBytes())).isBetween(1001L, 1500L);
    }

    @Test
    public void testExpireCommandsHonourOptions() {
        ReactiveKeyCommands keyCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().keyCommands();
        byte[] key = "expire-options".getBytes();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        connection.stringCommands().set(key, "value".getBytes());
        connection.keyCommands().expire(key, 1000);
        ExpirationOptions nx = ExpirationOptions.builder().nx().build();
        ExpirationOptions lt = ExpirationOptions.builder().lt().build();

        assertThat(keyCommands.expire(Mono.just(ExpireCommand.key(keyBuf).timeout(Duration.ofSeconds(100)).withOptions(nx)))
                .blockFirst().getOutput()).isFalse();
        assertThat(keyCommands.pExpire(Mono.just(ExpireCommand.key(keyBuf).timeout(Duration.ofSeconds(100)).withOptions(nx)))
                .blockFirst().getOutput()).isFalse();
        assertThat(keyCommands.expireAt(Mono.just(ExpireAtCommand.key(keyBuf).timeout(Instant.now().plusSeconds(100)).withOptions(nx)))
                .blockFirst().getOutput()).isFalse();
        assertThat(keyCommands.pExpireAt(Mono.just(ExpireAtCommand.key(keyBuf).timeout(Instant.now().plusSeconds(5000)).withOptions(lt)))
                .blockFirst().getOutput()).isFalse();
        assertThat(connection.keyCommands().ttl(key)).isGreaterThan(900);

        assertThat(keyCommands.pExpireAt(Mono.just(ExpireAtCommand.key(keyBuf).timeout(Instant.now().plusSeconds(100)).withOptions(lt)))
                .blockFirst().getOutput()).isTrue();
        assertThat(connection.keyCommands().ttl(key)).isBetween(1L, 100L);
    }

    @Test
    public void testCopyWithReplace() {
        ReactiveKeyCommands keyCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().keyCommands();
        connection.stringCommands().set("copy-source".getBytes(), "source".getBytes());
        connection.stringCommands().set("copy-target".getBytes(), "target".getBytes());
        ByteBuffer source = ByteBuffer.wrap("copy-source".getBytes());
        ByteBuffer target = ByteBuffer.wrap("copy-target".getBytes());

        assertThat(keyCommands.copy(source, target, false).block()).isFalse();
        assertThat(connection.stringCommands().get("copy-target".getBytes())).isEqualTo("target".getBytes());
        assertThat(keyCommands.copy(source, target, true).block()).isTrue();
        assertThat(connection.stringCommands().get("copy-target".getBytes())).isEqualTo("source".getBytes());
    }

    @Test
    public void testRandomKey() {
        ReactiveKeyCommands keyCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().keyCommands();
        assertThat(keyCommands.randomKey().block()).isNull();

        connection.stringCommands().set("random-key".getBytes(), "value".getBytes());
        ByteBuffer key = keyCommands.randomKey().block();
        assertThat(key).isEqualTo(ByteBuffer.wrap("random-key".getBytes()));
    }

    @Test
    public void testScanWithTypeAndBinaryKeys() {
        ReactiveKeyCommands keyCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().keyCommands();
        byte[] binaryKey = {'b', (byte) 0xFF, 'k'};
        connection.stringCommands().set(binaryKey, "value".getBytes());
        connection.stringCommands().set("scan-string".getBytes(), "value".getBytes());
        connection.zSetCommands().zAdd("scan-zset".getBytes(), 1, "a".getBytes());

        assertThat(keyCommands.scan(KeyScanOptions.scanOptions(DataType.ZSET).build()).collectList().block())
                .containsExactly(ByteBuffer.wrap("scan-zset".getBytes()));
        assertThat(keyCommands.scan(ScanOptions.scanOptions().match(new byte[] {'b', (byte) 0xFF, '*'}).build()).collectList().block())
                .containsExactly(ByteBuffer.wrap(binaryKey));
        assertThat(keyCommands.scan(ScanOptions.scanOptions().match("b*").build()).collectList().block())
                .containsExactly(ByteBuffer.wrap(binaryKey));
    }

    @Test
    public void testPending() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveStringRedisTemplate t = new ReactiveStringRedisTemplate(factory);
        t.opsForStream().createGroup("test", ReadOffset.latest(), "testGroup").block();

        t.opsForStream().add("test", Collections.singletonMap("1", "1")).block();

        assertThat(t.opsForStream().pending("test", "testGroup").block().getTotalPendingMessages()).isEqualTo(0);

        t.opsForStream().read(Consumer.from("testGroup", "test1"), StreamOffset.create("test", ReadOffset.from(">"))).single().block();

        PendingMessages msg = t.opsForStream().pending("test", "testGroup", Range.unbounded(), 10).block();
        assertThat(msg.size()).isEqualTo(1);
    }

    @Test
    public void testKeysKeepsBinaryKeys() {
        byte[] key = {'b', (byte) 0xFF, (byte) 0xFE, 'k'};
        connection.set(key, "value".getBytes());

        ReactiveKeyCommands keyCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().keyCommands();
        List<ByteBuffer> keys = keyCommands.keys(ByteBuffer.wrap("b*".getBytes())).block();

        assertThat(keys).containsExactly(ByteBuffer.wrap(key));
    }

}
