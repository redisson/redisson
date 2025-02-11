package org.redisson.jcache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.api.CacheAsync;
import org.redisson.api.CacheReactive;
import org.redisson.api.CacheRx;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.jcache.configuration.RedissonConfiguration;

import javax.cache.Cache;
import javax.cache.Caching;
import javax.cache.configuration.*;
import javax.cache.event.*;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class JCacheTest extends RedisDockerTest {

//    @BeforeEach
//    public void beforeEach() throws IOException, InterruptedException {
//        org.testcontainers.containers.Container.ExecResult r = REDIS.execInContainer("redis-cli", "flushall");
//        assertThat(r.getExitCode()).isEqualTo(0);
//    }

    <K, V> MutableConfiguration<K, V> createJCacheConfig() {
        return new MutableConfiguration<>();
    }

    @Test
    public void testYAML() throws IOException {
        URI configUrl = resolve("redisson-jcache.yaml", REDIS.getFirstMappedPort());
        Config cfg = Config.fromYAML(configUrl.toURL());

        MutableConfiguration<String, String> c = createJCacheConfig();
        c.setStatisticsEnabled(true);
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg, c);

        Cache<String, String> cache1 = Caching.getCachingProvider()
                .getCacheManager().createCache("test1", config);
        cache1.put("1", "2");
        assertThat(cache1.get("1")).isEqualTo("2");
        cache1.close();

        Cache<String, String> cache2 = Caching.getCachingProvider().getCacheManager(configUrl, null)
                .createCache("test2", config);
        cache2.put("3", "4");
        assertThat(cache2.get("3")).isEqualTo("4");
        cache2.close();
    }

    public URI resolve(String filename, int serverPort) throws IOException {
        File inputFile = new File(getClass().getResource(filename).getFile());
        String content = new String(Files.readAllBytes(inputFile.toPath()));

        content = content.replace("${port}", String.valueOf(serverPort));

        Path tempFile = Files.createTempFile("modified_", "_" + filename);
        Files.write(tempFile, content.getBytes());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        return tempFile.toUri();
    }
    @Test
    public void testClose() {
        MutableConfiguration<String, String> c = createJCacheConfig();
        c.setStatisticsEnabled(true);
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider()
                                                .getCacheManager().createCache("test", config);
        cache.close();
    }

    @Test
    public void testCreatedExpiryPolicy() throws Exception {
        MutableConfiguration<String, String> c = createJCacheConfig();
        c.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(MILLISECONDS, 500)));
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("1", "2");
        Thread.sleep(1000);
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
    }

    @Test
    public void testClear() {
        Configuration<Integer, Integer> c = createJCacheConfig();
        Configuration<Integer, Integer> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<Integer, Integer> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        for (int i = 0; i < 100; i++) {
            cache.put(i, i);
        }
        cache.clear();
        for (int i = 0; i < 100; i++) {
            assertThat(cache.get(i)).isNull();
        }

        cache.close();
    }

    @Test
    public void testAsync() throws Exception {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        CacheAsync<String, String> async = cache.unwrap(CacheAsync.class);
        async.putAsync("1", "2").get();
        assertThat(async.getAsync("1").get()).isEqualTo("2");

        cache.close();
    }

    @Test
    public void testReactive() {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        CacheReactive<String, String> reactive = cache.unwrap(CacheReactive.class);
        reactive.put("1", "2").block();
        assertThat(reactive.get("1").block()).isEqualTo("2");

        cache.close();
    }

    @Test
    public void testRx() {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        CacheRx<String, String> rx = cache.unwrap(CacheRx.class);
        rx.put("1", "2").blockingAwait();
        assertThat(rx.get("1").blockingGet()).isEqualTo("2");

        cache.close();
    }

    @Test
    public void testPutAll() throws Exception {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
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
    }

    @Test
    public void testRemoveAll() throws Exception {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("1", "2");
        cache.put("3", "4");
        cache.put("4", "4");
        cache.put("5", "5");

        Set<? extends String> keys = new HashSet<>(Arrays.asList("1", "3", "4", "5"));
        cache.removeAll(keys);
        assertThat(cache.containsKey("1")).isFalse();
        assertThat(cache.containsKey("3")).isFalse();
        assertThat(cache.containsKey("4")).isFalse();
        assertThat(cache.containsKey("5")).isFalse();

        cache.close();
    }

    @Test
    public void testGetAllHighVolume() {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
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
    }

    @Test
    public void testGetAll() {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("1", "2");
        cache.put("3", "4");

        Map<String, String> entries = cache.getAll(new HashSet<>(Arrays.asList("1", "3", "7")));
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "2");
        expected.put("3", "4");
        assertThat(entries).isEqualTo(expected);

        cache.close();
    }

    @Test
    public void testGetAllCacheLoader() throws Exception {
        MutableConfiguration<String, String> jcacheConfig = createJCacheConfig();
        jcacheConfig.setReadThrough(true);
        jcacheConfig.setCacheLoaderFactory(new Factory<CacheLoader<String, String>>() {
            @Override
            public CacheLoader<String, String> create() {
                return new CacheLoader<String, String>() {
                    @Override
                    public String load(String key) throws CacheLoaderException {
                        throw new CacheLoaderException("shouldn't be used");
                    }

                    @Override
                    public Map<String, String> loadAll(Iterable<? extends String> keys) throws CacheLoaderException {
                        Map<String, String> res = new HashMap<>();
                        for (String key : keys) {
                            res.put(key, key+"_loaded");
                        }
                        return res;
                    }
                };
            }
        });
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, jcacheConfig);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("1", "2");
        cache.put("3", "4");

        Map<String, String> entries = cache.getAll(new HashSet<>(Arrays.asList("1", "3", "7", "10")));
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "2");
        expected.put("3", "4");
        expected.put("7", "7_loaded");
        expected.put("10", "10_loaded");
        assertThat(entries).isEqualTo(expected);

        cache.close();
    }

    @Test
    public void testJson() throws IllegalArgumentException, IOException {
        URL configUrl = resolve("redisson-jcache.yaml", REDIS.getFirstMappedPort()).toURL();
        Config cfg = Config.fromYAML(configUrl);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        cfg.setCodec(new TypedJsonJacksonCodec(String.class, LocalDateTime.class, objectMapper));

        Configuration<String, LocalDateTime> c = createJCacheConfig();
        Configuration<String, LocalDateTime> config = RedissonConfiguration.fromConfig(cfg, c);
        Cache<String, LocalDateTime> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        LocalDateTime t = LocalDateTime.now();
        cache.put("1", t);
        Assertions.assertEquals(t, cache.get("1"));

        cache.close();
    }

    @Test
    public void testGetAndPut() {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("key", "value");
        assertThat(cache.getAndPut("key", "value1")).isEqualTo("value");
        assertThat(cache.get("key")).isEqualTo("value1");

        cache.close();
    }

    @Test
    public void testGetAndReplace() {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        assertThat(cache.getAndReplace("key", "value1")).isNull();
        assertThat(cache.get("key")).isNull();

        cache.put("key", "value");
        assertThat(cache.getAndReplace("key", "value1")).isEqualTo("value");
        assertThat(cache.get("key")).isEqualTo("value1");

        cache.close();
    }

    @Test
    public void testRedissonConfig() throws IllegalArgumentException {
        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, c);
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
    }

    @Test
    public void testScriptCache() throws IOException {
        URL configUrl = resolve("redisson-jcache.yaml", REDIS.getFirstMappedPort()).toURL();
        Config cfg = Config.fromYAML(configUrl);
        cfg.setUseScriptCache(true);

        Configuration<String, String> c = createJCacheConfig();
        Configuration<String, String> config = RedissonConfiguration.fromConfig(cfg, c);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
                .createCache("test", config);

        cache.put("1", "2");
        Assertions.assertEquals("2", cache.get("1"));

        cache.close();
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
    public void testExpiration() throws InterruptedException, IllegalArgumentException {
        MutableConfiguration<String, String> cfg = createJCacheConfig();
        cfg.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, 1)));
        cfg.setStoreByValue(true);

        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
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
    }

    @Test
    public void testUpdate() throws InterruptedException {
        MutableConfiguration<String, String> cfg = createJCacheConfig();
        cfg.setStoreByValue(true);

        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
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
    }

    @Test
    public void testUpdateAsync() throws InterruptedException {
        MutableConfiguration<String, String> cfg = createJCacheConfig();
        cfg.setStoreByValue(true);

        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
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
    }

    @Test
    public void testUpdateWithoutOldValue() throws InterruptedException {
        MutableConfiguration<String, String> cfg = createJCacheConfig();
        cfg.setStoreByValue(true);

        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
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
    }

    @Test
    public void testRemoveListener() throws InterruptedException {
        MutableConfiguration<String, String> cfg = createJCacheConfig();
        cfg.setStoreByValue(true);

        Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, cfg);
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager()
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
