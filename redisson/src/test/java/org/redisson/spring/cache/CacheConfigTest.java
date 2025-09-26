package org.redisson.spring.cache;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;
import java.util.Map;

/**
 * @author moil-xm
 * @since 2025-09-25 17:45
 */
public class CacheConfigTest {
    @Test
    public void test_fromConfig() {
        String prefix = "prefix";
        Duration ttl = Duration.ofSeconds(10);

        Map<String, RedisCacheConfiguration> redisCacheConfiguMap = Map.of(
                "test", RedisCacheConfiguration.defaultCacheConfig().computePrefixWith(CacheKeyPrefix.prefixed(prefix)).entryTtl(ttl),
                "test-empty", RedisCacheConfiguration.defaultCacheConfig()
        );
        Map<String, ? extends CacheConfig> redissonConfig = CacheConfig.fromConfig(redisCacheConfiguMap);
        // test
        CacheConfig testCacheConfig = redissonConfig.get("test");
        Assertions.assertNotNull(testCacheConfig);
        Assertions.assertEquals(prefix, testCacheConfig.getKeyPrefix());
        Assertions.assertEquals(ttl.toMillis(), testCacheConfig.getTTL());
        // test-empty
        CacheConfig testEmptyCacheConfig = redissonConfig.get("test-empty");
        Assertions.assertNotNull(testEmptyCacheConfig);
        Assertions.assertNull(testEmptyCacheConfig.getKeyPrefix());
        Assertions.assertEquals(0L, testEmptyCacheConfig.getTTL());
    }
}
