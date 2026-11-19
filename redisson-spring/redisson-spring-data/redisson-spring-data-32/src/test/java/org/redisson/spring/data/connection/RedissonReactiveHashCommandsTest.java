package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.springframework.data.redis.connection.ReactiveHashCommands;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveHashCommandsTest extends BaseConnectionTest {

    private final byte[] key = "hash".getBytes(StandardCharsets.UTF_8);
    private final byte[] field = "field".getBytes(StandardCharsets.UTF_8);
    private final byte[] value = "value".getBytes(StandardCharsets.UTF_8);

    @Test
    public void testHScanWithBinaryPattern() {
        byte[] binaryField = {(byte) 0xFF, 'f'};
        connection.hashCommands().hSet(key, binaryField, value);
        connection.hashCommands().hSet(key, field, value);
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();

        assertThat(hashCommands.hScan(ByteBuffer.wrap(key), ScanOptions.scanOptions().match(new byte[] {(byte) 0xFF, '*'}).build())
                .map(Map.Entry::getKey).collectList().block()).containsExactly(ByteBuffer.wrap(binaryField));
    }

    @Test
    public void testFieldsWithoutValues() {
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();
        ByteBuffer hash = ByteBuffer.wrap(key);
        ByteBuffer missing = ByteBuffer.wrap("missing".getBytes(StandardCharsets.UTF_8));

        assertThat(hashCommands.hMGet(hash, Collections.singletonList(missing)).block()).containsExactly((ByteBuffer) null);

        connection.hashCommands().hSet(key, field, value);
        List<ByteBuffer> fields = Arrays.asList(ByteBuffer.wrap(field), missing);
        assertThat(hashCommands.hMGet(hash, fields).block()).containsExactly(ByteBuffer.wrap(value), null);
        connection.hashCommands().hDel(key, field);
        assertThat(hashCommands.hMGet(hash, fields).block()).containsExactly(null, null);
    }

    @Test
    public void testHRandField() {
        connection.hashCommands().hSet(key, field, value);
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();

        assertThat(hashCommands.hRandField(ByteBuffer.wrap(key)).block()).isEqualTo(ByteBuffer.wrap(field));
        assertThat(hashCommands.hRandField(ByteBuffer.wrap(key), 2).collectList().block()).containsExactly(ByteBuffer.wrap(field));
        assertThat(hashCommands.hRandField(ByteBuffer.wrap(key), -2).collectList().block())
                .containsExactly(ByteBuffer.wrap(field), ByteBuffer.wrap(field));
    }

    @Test
    public void testHRandFieldWithValues() {
        connection.hashCommands().hSet(key, field, value);
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();

        Map.Entry<ByteBuffer, ByteBuffer> entry = hashCommands.hRandFieldWithValues(ByteBuffer.wrap(key)).block();
        assertThat(entry.getKey()).isEqualTo(ByteBuffer.wrap(field));
        assertThat(entry.getValue()).isEqualTo(ByteBuffer.wrap(value));
        assertThat(hashCommands.hRandFieldWithValues(ByteBuffer.wrap(key), 2).collectList().block())
                .extracting(Map.Entry::getValue).containsExactly(ByteBuffer.wrap(value));
        assertThat(hashCommands.hRandFieldWithValues(ByteBuffer.wrap(key), -2).collectList().block()).hasSize(2);

        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            ReactiveHashCommands resp3 = new RedissonConnectionFactory(client).getReactiveConnection().hashCommands();
            assertThat(resp3.hRandFieldWithValues(ByteBuffer.wrap(key), 2).collectList().block())
                    .extracting(Map.Entry::getValue).containsExactly(ByteBuffer.wrap(value));
        } finally {
            client.shutdown();
        }
    }
}
