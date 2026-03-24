package org.redisson.spring.starter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringJUnitConfig
@SpringBootTest(
        classes = RedissonApplication.class,
        properties = {
            "spring.redis.redisson.file=classpath:redisson.yaml",
            "spring.redis.timeout=10000",
            "spring.cache.type=redis",
        })
@Testcontainers
public class RedissonCacheManagerAutoConfigurationTest {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
            .withFixedExposedPort(6379, 6379)
            .withCommand("redis-server", "--requirepass", "123456");

    @Autowired
    private CacheManager cacheManager;
    
    @Test
    public void testApp() {
        Cache cache = cacheManager.getCache("test");
        Assertions.assertThat(cache).isNotNull();
    }
    
}
