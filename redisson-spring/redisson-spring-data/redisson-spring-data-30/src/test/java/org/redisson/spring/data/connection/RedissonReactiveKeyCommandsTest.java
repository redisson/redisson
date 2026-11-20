package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.ReactiveKeyCommands;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveKeyCommandsTest extends BaseConnectionTest {

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
    public void testKeysKeepsBinaryKeys() {
        byte[] key = {'b', (byte) 0xFF, (byte) 0xFE, 'k'};
        connection.set(key, "value".getBytes());

        ReactiveKeyCommands keyCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().keyCommands();
        List<ByteBuffer> keys = keyCommands.keys(ByteBuffer.wrap("b*".getBytes())).block();

        assertThat(keys).containsExactly(ByteBuffer.wrap(key));
    }

}
