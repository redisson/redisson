package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class CommandHandlersTest extends BaseTest {

    @Test
    public void testEncoder() throws InterruptedException {
        Assertions.assertThrows(RuntimeException.class, () -> {
            Config config = createConfig();
            config.setCodec(new ErrorsCodec());

            RedissonClient redisson = Redisson.create(config);

            redisson.getBucket("1234").set("1234");
        });
    }
    
    @Test
    public void testDecoder() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            redisson.getBucket("1234").set("1234");

            Config config = createConfig();
            config.setCodec(new ErrorsCodec());

            RedissonClient redisson = Redisson.create(config);

            redisson.getBucket("1234").get();
        });
    }
    
}
