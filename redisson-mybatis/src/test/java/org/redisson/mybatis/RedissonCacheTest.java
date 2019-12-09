package org.redisson.mybatis;

import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheTest {

    private RedissonCache cache;

    @Before
    public void before() {
        cache = new RedissonCache("redisson");
        cache.setTimeToLive(1000);
        cache.setRedissonConfig("redisson.yaml");
    }

    @Test
    public void testPut() throws InterruptedException {
        cache.putObject("1", "2");
        assertThat(cache.getObject("1")).isEqualTo("2");
        assertThat(cache.getSize()).isEqualTo(1);
        Thread.sleep(1000);
        assertThat(cache.getObject("1")).isNull();
    }

}
