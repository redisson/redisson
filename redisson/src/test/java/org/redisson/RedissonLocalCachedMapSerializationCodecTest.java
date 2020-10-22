package org.redisson;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.api.RedissonClient;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;

import java.io.IOException;

/**
 * Created by jribble on 1/12/17.
 */
public class RedissonLocalCachedMapSerializationCodecTest extends RedissonLocalCachedMapTest {
    public static Config createConfig() {
        Config config = RedissonLocalCachedMapTest.createConfig();
        config.setCodec(new SerializationCodec());
        return config;
    }

    public static RedissonClient createInstance() {
        Config config = createConfig();
        return Redisson.create(config);
    }

    @Test @Override
    public void testAddAndGet() throws InterruptedException {
        // this method/test won't work with Java Serialization
    }
}
