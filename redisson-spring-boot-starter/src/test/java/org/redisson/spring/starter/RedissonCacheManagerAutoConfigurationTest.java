package org.redisson.spring.starter;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = RedissonApplication.class,
        properties = {
            "spring.redis.redisson.config=classpath:redisson.yaml",
            "spring.redis.timeout=10000",
            "spring.cache.type=redis",
        })
public class RedissonCacheManagerAutoConfigurationTest {

    @Autowired
    private CacheManager cacheManager;
    
    @Test
    public void testApp() {
        Cache cache = cacheManager.getCache("test");
        Assert.assertNotNull(cache);
    }
    
}
