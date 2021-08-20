package org.redisson.spring.starter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@SpringBootTest(
        classes = RedissonApplication.class,
        properties = {
            "spring.redis.redisson.file=classpath:redisson.yaml",
            "spring.redis.timeout=10000",
            "spring.cache.type=redis",
        })
public class RedissonCacheManagerAutoConfigurationTest {

    @Autowired
    private CacheManager cacheManager;
    
    @Test
    public void testApp() {
        Cache cache = cacheManager.getCache("test");
        Assertions.assertThat(cache).isNotNull();
    }
    
}
