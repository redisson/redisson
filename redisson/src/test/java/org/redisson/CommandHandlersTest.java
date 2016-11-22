package org.redisson;

import org.junit.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;

public class CommandHandlersTest extends AbstractBaseTest {

    @Test(expected = RedisException.class)
    public void testEncoder() throws InterruptedException {
        Config config = redissonRule.getSharedConfig();
        config.setCodec(new ErrorsCodec());
        
        RedissonClient redisson = redissonRule.createClient(config);
        
        redisson.getBucket("1234").set("1234");
    }
    
    @Test(expected = RedisException.class)
    public void testDecoder() {
        redissonRule.getSharedClient().getBucket("1234").set("1234");
        
        Config config = redissonRule.getSharedConfig();
        config.setCodec(new ErrorsCodec());
        
        RedissonClient redisson = redissonRule.createClient(config);
        
        redisson.getBucket("1234").get();
    }
}
