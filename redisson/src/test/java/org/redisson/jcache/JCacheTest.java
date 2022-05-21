package org.redisson.jcache;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.BaseTest;
import org.redisson.RedisRunner;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.CacheAsync;
import org.redisson.api.CacheReactive;
import org.redisson.api.CacheRx;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.jcache.configuration.RedissonConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JCacheTest extends BaseTest {

    @Test
    public void testCreatedExpiryPolicy() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();

        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);

        MutableConfiguration c = new MutableConfiguration();
        c.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(MILLISECONDS, 500)));
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("1", "2");
        Thread.sleep(500);
        assertThat(cache.get("1")).isNull();
        cache.put("1", "3");
        assertThat(cache.get("1")).isEqualTo("3");
        Thread.sleep(500);
        assertThat(cache.get("1")).isNull();

        cache.put("1", "4");
        assertThat(cache.get("1")).isEqualTo("4");
        Thread.sleep(100);
        cache.put("1", "5");
        assertThat(cache.get("1")).isEqualTo("5");

        cache.close();
        runner.stop();
    }

    @Test
    public void testClear() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();

        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);

        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("1", "2");
        cache.clear();
        assertThat(cache.get("1")).isNull();

        cache.close();
        runner.stop();
    }

    @Test
    public void testAsync() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        CacheAsync<String, String> async = cache.unwrap(CacheAsync.class);
        async.putAsync("1", "2").get();
        assertThat(async.getAsync("1").get()).isEqualTo("2");
        
        cache.close();
        runner.stop();
    }
    
    @Test
    public void testReactive() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        CacheReactive<String, String> reactive = cache.unwrap(CacheReactive.class);
        reactive.put("1", "2").block();
        assertThat(reactive.get("1").block()).isEqualTo("2");
        
        cache.close();
        runner.stop();
    }
    
    @Test
    public void testRx() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        CacheRx<String, String> rx = cache.unwrap(CacheRx.class);
        rx.put("1", "2").blockingAwait();
        assertThat(rx.get("1").blockingGet()).isEqualTo("2");
        
        cache.close();
        runner.stop();
    }
    
    @Test
    public void testPutAll() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);
        
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            map.put("" + i, "" + i);
        }
        
        long start = System.currentTimeMillis();
        cache.putAll(map);
        System.out.println(System.currentTimeMillis() - start);
        
        for (int i = 0; i < 10000; i++) {
            assertThat(cache.containsKey("" + i)).isTrue();
        }
        
        cache.close();
        runner.stop();
    }
    
    @Test
    public void testRemoveAll() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);
        
        cache.put("1", "2");
        cache.put("3", "4");
        cache.put("4", "4");
        cache.put("5", "5");
        
        Set<? extends String> keys = new HashSet<String>(Arrays.asList("1", "3", "4", "5"));
        cache.removeAll(keys);
        assertThat(cache.containsKey("1")).isFalse();
        assertThat(cache.containsKey("3")).isFalse();
        assertThat(cache.containsKey("4")).isFalse();
        assertThat(cache.containsKey("5")).isFalse();
        
        cache.close();
        runner.stop();
    }

    @Test
    public void testGetAllHighVolume() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            m.put("" + i, "" + i);
        }
        cache.putAll(m);
        
        Map<String, String> entries = cache.getAll(m.keySet());
        assertThat(entries).isEqualTo(m);
        
        cache.close();
        runner.stop();
    }
    
    @Test
    public void testGetAll() throws Exception {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);
        
        cache.put("1", "2");
        cache.put("3", "4");
        
        Map<String, String> entries = cache.getAll(new HashSet<String>(Arrays.asList("1", "3", "7")));
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("1", "2");
        expected.put("3", "4");
        assertThat(entries).isEqualTo(expected);
        
        cache.close();
        runner.stop();
    }
    
    @Test
    public void testJson() throws InterruptedException, IllegalArgumentException, URISyntaxException, IOException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        cfg.setCodec(new TypedJsonJacksonCodec(String.class, LocalDateTime.class, objectMapper));
        
        Configuration<String, LocalDateTime> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, LocalDateTime> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);
        
        LocalDateTime t = LocalDateTime.now();
        cache.put("1", t);
        Assertions.assertEquals(t, cache.get("1"));
        
        cache.close();
        runner.stop();
    }

    @Test
    public void testRedissonConfig() throws InterruptedException, IllegalArgumentException, IOException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();
        
        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);
        
        cache.put("1", "2");
        Assertions.assertEquals("2", cache.get("1"));
        
        cache.put("key", "value");
        String result = cache.getAndRemove("key");

        Assertions.assertEquals("value", result);
        Assertions.assertNull(cache.get("key"));

        cache.put("key", "value");
        cache.remove("key");
        Assertions.assertNull(cache.get("key"));
        
        cache.close();
        runner.stop();
    }

    @Test
    public void testScriptCache() throws IOException, InterruptedException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();

        URL configUrl = getClass().getResource("redisson-jcache.yaml");
        Config cfg = Config.fromYAML(configUrl);
        cfg.setUseScriptCache(true);

        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("1", "2");
        Assertions.assertEquals("2", cache.get("1"));

        cache.close();
        runner.stop();
    }

    @Test
    public void testRedissonInstance() throws IllegalArgumentException {
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);
        
        cache.put("1", "2");
        Assertions.assertEquals("2", cache.get("1"));
        
        cache.close();
    }

    @Test
    public void testExpiration() throws InterruptedException, IllegalArgumentException, URISyntaxException, FailedToStartRedisException, IOException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();

        MutableConfiguration<String, String> config = new MutableConfiguration<>();
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, 1)));
        config.setStoreByValue(true);
        
        URI configUri = getClass().getResource("redisson-jcache.yaml").toURI();
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager(configUri, null)
                .createCache("test", config);

        CountDownLatch latch = new CountDownLatch(1);
        
        String key = "123";
        ExpiredListener clientListener = new ExpiredListener(latch, key, "90");
        MutableCacheEntryListenerConfiguration<String, String> listenerConfiguration =
                new MutableCacheEntryListenerConfiguration<>(FactoryBuilder.factoryOf(clientListener), null, true, true);
        cache.registerCacheEntryListener(listenerConfiguration);

        cache.put(key, "90");
        Assertions.assertNotNull(cache.get(key));
        
        latch.await();
        
        Assertions.assertNull(cache.get(key));
        
        cache.close();
        runner.stop();
    }

    @Test
    public void testUpdate() throws IOException, InterruptedException, URISyntaxException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();

        MutableConfiguration<String, String> config = new MutableConfiguration<>();
        config.setStoreByValue(true);

        URI configUri = getClass().getResource("redisson-jcache.yaml").toURI();
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager(configUri, null)
                .createCache("test", config);

        CountDownLatch latch = new CountDownLatch(1);

        String key = "123";

        UpdatedListener clientListener = new UpdatedListener(latch, key, "80", "90");
        MutableCacheEntryListenerConfiguration<String, String> listenerConfiguration =
                new MutableCacheEntryListenerConfiguration<>(FactoryBuilder.factoryOf(clientListener), null, true, true);
        cache.registerCacheEntryListener(listenerConfiguration);

        cache.put(key, "80");
        assertThat(cache.get(key)).isNotNull();

        cache.put(key, "90");

        latch.await();

        assertThat(cache.get(key)).isNotNull();

        cache.close();
        runner.stop();
    }

    @Test
    public void testUpdateAsync() throws IOException, InterruptedException, URISyntaxException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();

        MutableConfiguration<String, String> config = new MutableConfiguration<>();
        config.setStoreByValue(true);

        URI configUri = getClass().getResource("redisson-jcache.yaml").toURI();
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager(configUri, null)
                .createCache("test", config);

        CountDownLatch latch = new CountDownLatch(2);

        String key = "123";

        UpdatedListener clientListener = new UpdatedListener(latch, key, "80", "90");
        MutableCacheEntryListenerConfiguration<String, String> listenerConfiguration =
                new MutableCacheEntryListenerConfiguration<>(FactoryBuilder.factoryOf(clientListener), null, true, false);
        cache.registerCacheEntryListener(listenerConfiguration);

        UpdatedListener secondClientListener = new UpdatedListener(latch, key, "80", "90");
        MutableCacheEntryListenerConfiguration<String, String> secondListenerConfiguration =
                new MutableCacheEntryListenerConfiguration<>(FactoryBuilder.factoryOf(secondClientListener), null, false, false);
        cache.registerCacheEntryListener(secondListenerConfiguration);

        cache.put(key, "80");
        assertThat(cache.get(key)).isNotNull();

        cache.put(key, "90");

        latch.await();

        assertThat(cache.get(key)).isNotNull();

        cache.close();
        runner.stop();
    }

    @Test
    public void testUpdateWithoutOldValue() throws IOException, InterruptedException, URISyntaxException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();

        MutableConfiguration<String, String> config = new MutableConfiguration<>();
        config.setStoreByValue(true);

        URI configUri = getClass().getResource("redisson-jcache.yaml").toURI();
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager(configUri, null)
                .createCache("test", config);

        CountDownLatch latch = new CountDownLatch(1);

        String key = "123";

        UpdatedListener secondClientListener = new UpdatedListener(latch, key, null, "90");
        MutableCacheEntryListenerConfiguration<String, String> secondListenerConfiguration =
                new MutableCacheEntryListenerConfiguration<>(FactoryBuilder.factoryOf(secondClientListener), null, false, true);
        cache.registerCacheEntryListener(secondListenerConfiguration);

        cache.put(key, "80");
        assertThat(cache.get(key)).isNotNull();

        cache.put(key, "90");

        latch.await();

        assertThat(cache.get(key)).isNotNull();

        cache.close();
        runner.stop();
    }

    @Test
    public void testRemoveListener() throws IOException, InterruptedException, URISyntaxException {
                RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .port(6311)
                .run();

        MutableConfiguration<String, String> config = new MutableConfiguration<>();
        config.setStoreByValue(true);

        URI configUri = getClass().getResource("redisson-jcache.yaml").toURI();
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager(configUri, null)
                .createCache("test", config);

        CountDownLatch latch = new CountDownLatch(1);

        String key = "123";

        RemovedListener clientListener = new RemovedListener(latch, key, "80");
        MutableCacheEntryListenerConfiguration<String, String> listenerConfiguration =
                new MutableCacheEntryListenerConfiguration<>(FactoryBuilder.factoryOf(clientListener), null, true, true);
        cache.registerCacheEntryListener(listenerConfiguration);

        cache.put(key, "80");
        assertThat(cache.get(key)).isNotNull();

        cache.remove(key);

        latch.await();

        assertThat(cache.get(key)).isNull();

        cache.close();
        runner.stop();
    }
    
    public static class ExpiredListener implements CacheEntryExpiredListener<String, String>, Serializable {

        private Object key;
        private Object value;
        private CountDownLatch latch;
        
        public ExpiredListener(CountDownLatch latch, Object key, Object value) {
            super();
            this.latch = latch;
            this.key = key;
            this.value = value;
        }



        @Override
        public void onExpired(Iterable<CacheEntryEvent<? extends String, ? extends String>> events)
                throws CacheEntryListenerException {
            CacheEntryEvent<? extends String, ? extends String> entry = events.iterator().next();
            
            assertThat(entry.getKey()).isEqualTo(key);
            assertThat(entry.getValue()).isEqualTo(value);
            assertThat(entry.getOldValue()).isEqualTo(value);

            latch.countDown();
        }

        
    }
    
    public static class UpdatedListener implements CacheEntryUpdatedListener<String, String>, Serializable {
        private Object key;
        private Object oldValue;
        private Object value;
        private CountDownLatch latch;

        public UpdatedListener(CountDownLatch latch, Object key, Object oldValue, Object value) {
            super();
            this.latch = latch;
            this.key = key;
            this.oldValue = oldValue;
            this.value = value;
        }

        @Override
        public void onUpdated(Iterable<CacheEntryEvent<? extends String, ? extends String>> events)
                throws CacheEntryListenerException {
            CacheEntryEvent<? extends String, ? extends String> entry = events.iterator().next();

            assertThat(entry.getKey()).isEqualTo(key);
            assertThat(entry.getOldValue()).isEqualTo(oldValue);
            assertThat(entry.getValue()).isEqualTo(value);

            latch.countDown();
        }
    }

    public static class RemovedListener implements CacheEntryRemovedListener<String, String>, Serializable {
        private Object key;
        private Object value;
        private CountDownLatch latch;

        public RemovedListener(CountDownLatch latch, Object key, Object value) {
            super();
            this.latch = latch;
            this.key = key;
            this.value = value;
        }

        @Override
        public void onRemoved(Iterable<CacheEntryEvent<? extends String, ? extends String>> events)
                throws CacheEntryListenerException {
            CacheEntryEvent<? extends String, ? extends String> entry = events.iterator().next();

            assertThat(entry.getKey()).isEqualTo(key);
            assertThat(entry.getValue()).isEqualTo(value);
            assertThat(entry.getOldValue()).isEqualTo(value);

            latch.countDown();
        }
    }
}
