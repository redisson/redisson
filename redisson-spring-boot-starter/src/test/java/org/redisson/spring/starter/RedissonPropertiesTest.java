package org.redisson.spring.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = RedissonApplication.class,
        properties = {
                "spring.redis.redisson.configuration.singleServerConfig.address=redis://127.0.0.1:6379",
                "spring.redis.redisson.configuration.singleServerConfig.database=0",
                // Spring Boot does not allow custom YAML types: https://github.com/spring-projects/spring-boot/issues/21596
                // In a redisson-specific YAML you could set this value to "!<org.redisson.codec.JsonJacksonCodec> {}"
                // Now it requires a Converter from String (FQN) to instance.
                "spring.redis.redisson.configuration.codec=org.redisson.codec.JsonJacksonCodec"
        })
public class RedissonPropertiesTest {
    @Autowired
    private RedissonProperties redissonProperties;

    @Test
    void readConfigurationCorrectly() {
        assert redissonProperties.getConfiguration() != null;
    }
}
