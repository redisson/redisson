package org.redisson.spring.data.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class PropertiesDecoderTest {

    private final PropertiesDecoder decoder = new PropertiesDecoder();

    private final String info = "# Server\r\n" +
            "redis_version:5.0.10\r\n" +
            "redis_build_id:b9312d2bb5f35b93\r\n" +
            "redis_mode:standalone\n" +
            "executable:/data/redis-server\n" +
            "config_file:\n" +
            "" +
            "# Client";

    @Test
    public void testDecode() {
        Properties p = decoder.decode(Unpooled.copiedBuffer(info, StandardCharsets.UTF_8), null);
        Assert.assertEquals(p.getProperty("redis_version"), "5.0.10");
        Assert.assertEquals(p.getProperty("redis_mode"), "standalone");
        Assert.assertNull(p.getProperty("config_file"));
    }


    // Codes below should be deleted before merge

    private class PropertiesDecoderOld implements Decoder<Properties> {

        @Override
        public Properties decode(ByteBuf buf, State state) {
            String value = buf.toString(CharsetUtil.UTF_8);
            Properties result = new Properties();
            for (String entry : value.split("\r\n|\n")) {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    result.put(parts[0], parts[1]);
                }
            }
            return result;
        }
    }

//    @Test
    public void benchmark() {
        PropertiesDecoderOld old = new PropertiesDecoderOld();
        long start = System.currentTimeMillis();
        int c = 400000;
        while (c-- > 0) {
            old.decode(Unpooled.copiedBuffer(info, StandardCharsets.UTF_8), null);
        }
        System.out.println(System.currentTimeMillis() - start);

        start = System.currentTimeMillis();
        c = 400000;
        while (c-- > 0) {
            testDecode();
        }
        System.out.println(System.currentTimeMillis() - start);
    }
}