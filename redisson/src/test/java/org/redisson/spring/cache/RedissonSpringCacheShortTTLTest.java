package org.redisson.spring.cache;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.RedisDockerTest;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSpringCacheShortTTLTest extends RedisDockerTest {

    public static class SampleObject implements Serializable {

        private String name;
        private String value;

        public SampleObject() {
        }

        public SampleObject(String name, String value) {
            super();
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

    }

    @Service
    public static class SampleBean {

        @CachePut(cacheNames = "testMap", key = "#p0")
        public SampleObject store(String key, SampleObject object) {
            return object;
        }

        @CachePut(cacheNames = "testMap", key = "#p0")
        public SampleObject storeNull(String key) {
            return null;
        }
        
        @CacheEvict(cacheNames = "testMap", key = "#p0")
        public void remove(String key) {
        }

        @Cacheable(cacheNames = "testMap", key = "#p0")
        public SampleObject read(String key) {
            throw new IllegalStateException();
        }
        
        @Cacheable(cacheNames = "testMap", key = "#p0", sync = true)
        public SampleObject readNullSync(String key) {
            return null;
        }

        @Cacheable(cacheNames = "testMap", key = "#p0")
        public SampleObject readNull(String key) {
            return null;
        }

    }

    @Configuration
    @ComponentScan
    @EnableCaching
    public static class Application {

        @Bean(destroyMethod = "shutdown")
        RedissonClient redisson() {
            return createInstance();
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) throws IOException {
            Map<String, CacheConfig> config = new HashMap<String, CacheConfig>();
            config.put("testMap", new CacheConfig(1 * 1000, 1 * 1000));
            return new RedissonSpringCacheManager(redissonClient, config);
        }

    }
    
    @Configuration
    @ComponentScan
    @EnableCaching
    public static class JsonConfigApplication {

        @Bean(destroyMethod = "shutdown")
        RedissonClient redisson() {
            return createInstance();
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) {
            return new RedissonSpringCacheManager(redissonClient, "classpath:/org/redisson/spring/cache/cache-config-shortTTL.json");
        }

    }

    private static Map<Class<?>, AnnotationConfigApplicationContext> contexts;

    public static List<Class<?>> data() {
        return Arrays.asList(Application.class, JsonConfigApplication.class);
    }

    @BeforeAll
    public static void before() {
        contexts = data().stream().collect(Collectors.toMap(e -> e, e -> new AnnotationConfigApplicationContext(e)));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testPutGet(Class<?> contextClass) throws InterruptedException {
        AnnotationConfigApplicationContext context = contexts.get(contextClass);
        SampleBean bean = context.getBean(SampleBean.class);
        bean.store("object1", new SampleObject("name1", "value1"));
        SampleObject s = bean.read("object1");
        assertThat(s.getName()).isEqualTo("name1");
        assertThat(s.getValue()).isEqualTo("value1");

        Thread.sleep(1100);

        Assertions.assertThrows(IllegalStateException.class, () -> {
            bean.read("object1");
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testPutGetSync(Class<?> contextClass) throws InterruptedException {
        AnnotationConfigApplicationContext context = contexts.get(contextClass);
        SampleBean bean = context.getBean(SampleBean.class);
        bean.readNullSync("object1");
        assertThat(bean.read("object1")).isNull();

        Thread.sleep(1100);

        Assertions.assertThrows(IllegalStateException.class, () -> {
            bean.read("object1");
        });
    }

}
