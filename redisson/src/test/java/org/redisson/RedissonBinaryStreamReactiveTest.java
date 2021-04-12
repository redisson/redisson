package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBinaryStreamReactive;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Nikita Koksharov
 */
public class RedissonBinaryStreamReactiveTest extends BaseReactiveTest {

    @Test
    public void testAsyncReadWrite() {
        RBinaryStreamReactive stream = redisson.getBinaryStream("test");

        ByteBuffer bb = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7});
        sync(stream.write(bb));

        stream.position(0);
        ByteBuffer b = ByteBuffer.allocate(7);
        sync(stream.read(b));

        b.flip();
        assertThat(b).isEqualByComparingTo(bb);
    }

}
