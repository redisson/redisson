package org.redisson.micronaut;

import io.micronaut.cache.AsyncCache;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.micronaut.cache.RedissonSyncCache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheTest {

    @Test
    public void testCache() throws InterruptedException {
        Map<String, Object> map = new HashMap<>();
        map.put("redisson.threads", "10");
        map.put("redisson.single-server-config.address", "redis://127.0.0.1:6379");
//        map.put("redisson.clusterServersConfig.scanInterval", "3333");
//        map.put("redisson.clusterServersConfig.nodeAddresses", Arrays.asList("redis://127.0.0.2:6379","redis://127.0.0.3:6379"));
        map.put("redisson.caches.test.expire-after-write", "10s");
        map.put("redisson.caches.test.expire-after-access", "3s");
        ApplicationContext ac = ApplicationContext.run(map);

        RedissonClient client = ac.getBean(RedissonClient.class);
        assertThat(client).isNotNull();
        RedissonSyncCache cache = ac.getBean(RedissonSyncCache.class, Qualifiers.byName("test"));
        cache.put(1, 2);
        Thread.sleep(3500);
        assertThat(cache.get(1, Integer.class).isPresent()).isFalse();
        cache.put(3, 4);
        Thread.sleep(2000);
        cache.get(3, Integer.class);
        Thread.sleep(2000);
        assertThat(cache.get(3, Integer.class).isPresent()).isTrue();
    }

    @Test
    public void testCacheYaml() throws InterruptedException {
        ApplicationContext ac = ApplicationContext.run();

        RedissonClient client = ac.getBean(RedissonClient.class);
        assertThat(client).isNotNull();
        RedissonSyncCache cache = ac.getBean(RedissonSyncCache.class, Qualifiers.byName("test"));
        cache.put(1, 2);
        Thread.sleep(3500);
        assertThat(cache.get(1, Integer.class).isPresent()).isFalse();
        cache.put(3, 4);
        Thread.sleep(2000);
        cache.get(3, Integer.class);
        Thread.sleep(2000);
        assertThat(cache.get(3, Integer.class).isPresent()).isTrue();
    }

    @Test
    public void testAsyncCache() throws ExecutionException, InterruptedException {
        Map<String, Object> map = new HashMap<>();
        map.put("redisson.threads", "10");
        map.put("redisson.single-server-config.address", "redis://127.0.0.1:6379");
        map.put("redisson.caches.test.expire-after-write", "3s");
        map.put("redisson.caches.test.expire-after-access", "3s");
        ApplicationContext ac = ApplicationContext.run(map);

        RedissonClient client = ac.getBean(RedissonClient.class);
        assertThat(client).isNotNull();

        client.getKeys().flushall();

        RedissonSyncCache cache = ac.getBean(RedissonSyncCache.class, Qualifiers.byName("test"));
        AsyncCache asyncCache = cache.async();
        assertThat(asyncCache.get(3, Integer.class).get()).isEqualTo(Optional.empty());

        CompletableFuture f = asyncCache.put(3, 4).toCompletableFuture();
        f.join();

        assertThat(asyncCache.get(3, Integer.class).get()).isEqualTo(Optional.of(4));

    }

}
