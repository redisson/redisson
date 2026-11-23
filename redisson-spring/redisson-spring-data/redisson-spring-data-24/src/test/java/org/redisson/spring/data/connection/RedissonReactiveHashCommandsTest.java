package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveHashCommands;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveHashCommandsTest extends BaseConnectionTest {

    private final byte[] key = "hash".getBytes(StandardCharsets.UTF_8);
    private final byte[] field = "field".getBytes(StandardCharsets.UTF_8);
    private final byte[] value = "value".getBytes(StandardCharsets.UTF_8);

    @Test
    public void testFieldsWithoutValues() {
        ReactiveHashCommands hashCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().hashCommands();
        ByteBuffer hash = ByteBuffer.wrap(key);
        ByteBuffer missing = ByteBuffer.wrap("missing".getBytes(StandardCharsets.UTF_8));

        assertThat(hashCommands.hMGet(hash, Collections.singletonList(missing)).block()).isNull();
        assertThat(hashCommands.hGet(hash, missing).block()).isNull();

        connection.hashCommands().hSet(key, field, value);
        List<ByteBuffer> fields = Arrays.asList(ByteBuffer.wrap(field), missing);
        assertThat(hashCommands.hMGet(hash, fields).block()).containsExactly(ByteBuffer.wrap(value), null);
        connection.hashCommands().hDel(key, field);
        assertThat(hashCommands.hMGet(hash, fields).block()).containsExactly(null, null);
    }
}
