package org.redisson;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.Kryo5Codec;
import org.redisson.codec.LZ4Codec;
import org.redisson.codec.LZ4CodecV2;
import org.redisson.config.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LZ4CodecV2Test extends RedisDockerTest {

    record Value(String value) {}

    @Test
    void roundTripDoesNotTruncate() throws Exception {
        Value[] payloads = {
                new Value("A".repeat(20)),
                new Value("ab".repeat(50)),
                new Value("hello world ".repeat(8)),
                new Value("x".repeat(70_000))
        };

        for (Value original : payloads) {
            Codec codec = new LZ4CodecV2(new Kryo5Codec());
            ByteBuf encoded = codec.getValueEncoder().encode(original);
            try {
                Object decoded = codec.getValueDecoder().decode(encoded, null);
                assertEquals(original, decoded);
            } finally {
                encoded.release();
            }
        }
    }

    @Test
    public void test1() {
        Config config = createConfig();
        config.setCodec(new LZ4Codec());
        RedissonClient r = Redisson.create(config);
        RBucket<String> s = r.getBucket("test1");
        s.set("12324");

        Config config2 = createConfig();
        config2.setCodec(new LZ4CodecV2());
        RedissonClient r2 = Redisson.create(config2);
        RBucket<String> s2 = r2.getBucket("test1");
        assertThat(s2.get()).isEqualTo("12324");

        r.shutdown();
        r2.shutdown();
    }

}
