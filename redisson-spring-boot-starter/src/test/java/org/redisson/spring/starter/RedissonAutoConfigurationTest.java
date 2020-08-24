package org.redisson.spring.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = RedissonApplication.class,
        properties = {
            "spring.redis.redisson.config=classpath:redisson.yaml",
            "spring.redis.timeout=10000"
        })
public class RedissonAutoConfigurationTest {

    @Autowired
    private RedissonClient redisson;
    
    @Autowired
    private RedisTemplate<String, String> template;
    
    @Test
    public void testApp() {
        redisson.getKeys().flushall();
        
        RMap<String, String> m = redisson.getMap("test", StringCodec.INSTANCE);
        m.put("1", "2");
        
        BoundHashOperations<String, String, String> hash = template.boundHashOps("test");
        String t = hash.get("1");
        assertThat(t).isEqualTo("2");
    }
    
}
