package org.redisson.spring.starter;

import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@SpringBootTest(classes = RedissonApplication.class)
@Import(RedissonAutoConfiguration2Test.RedissonConnectionDetailsConfig.class)
@Testcontainers
public class RedissonAutoConfiguration2Test {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
            .withFixedExposedPort(6379, 6379);


    @TestConfiguration
    static class RedissonConnectionDetailsConfig {

        @Bean
        RedisConnectionDetails redisConnectionDetails() {
            return new RedisConnectionDetails() {
                @Override
                public Standalone getStandalone() {
                    return Standalone.of("localhost", 6379);
                }
            };
        }
    }

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
