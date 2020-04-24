package org.redisson.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MarshallingCodecTest {

    public static class NonSerializable {

    }

    @Test
    public void testDecodeNonSerializable() throws IOException {
        MarshallingCodec m = new MarshallingCodec();
        try {
            ByteBuf t = (ByteBuf) m.getValueDecoder().decode(Unpooled.copiedBuffer("adfsadf", CharsetUtil.UTF_8), null);
            Assertions.fail("Exception should be thrown");
        } catch (Exception e) {
            // skip
        }
        ByteBuf d = m.getValueEncoder().encode("test");
        Object s = m.getValueDecoder().decode(d, null);
        Assertions.assertThat(s).isEqualTo("test");
    }


    @Test
    public void testEncodeNonSerializable() throws IOException {
        MarshallingCodec m = new MarshallingCodec();
        try {
            ByteBuf t = m.getValueEncoder().encode(new NonSerializable());
            Assertions.fail("Exception should be thrown");
        } catch (Exception e) {
            // skip
        }
        ByteBuf d = m.getValueEncoder().encode("test");
        Object s = m.getValueDecoder().decode(d, null);
        Assertions.assertThat(s).isEqualTo("test");
    }

}
