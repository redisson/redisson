package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveKeyCommands;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveKeyCommandsTest extends BaseConnectionTest {

    @Test
    public void testRandomKey() {
        ReactiveKeyCommands keyCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().keyCommands();
        assertThat(keyCommands.randomKey().block()).isNull();

        connection.stringCommands().set("random-key".getBytes(), "value".getBytes());
        ByteBuffer key = keyCommands.randomKey().block();
        assertThat(key).isEqualTo(ByteBuffer.wrap("random-key".getBytes()));
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
