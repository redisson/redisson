package org.redisson.tairhash;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;

public class TairHashTest {

    @Test
    public void testTairHash1() throws InterruptedException {
        Config config = new Config();
        config.setProtocol(Protocol.RESP2);
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        RedissonClient redisClient = Redisson.create(config);
        RMap<String,Long> map = redisClient.getMap("test1");
        map.clear();
        map.exPut("field1",1L,10);
        map.addAndExGet("field1",1L,10);
        System.out.println(map.exGet("field1"));
    }
}
