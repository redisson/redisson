package org.redisson.spring.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.BaseTest;
import org.redisson.RedisRunner;
import org.redisson.RedisRunner.FailedToStartRedisException;
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
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
public class RedissonSpringCacheTest {

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

        @CachePut(cacheNames = "testMap", key = "#key")
        public SampleObject store(String key, SampleObject object) {
            return object;
        }

        @CachePut(cacheNames = "testMap", key = "#key")
        public SampleObject storeNull(String key) {
            return null;
        }

        @CacheEvict(cacheNames = "testMap", key = "#key")
        public void remove(String key) {
        }

        @Cacheable(cacheNames = "testMap", key = "#key")
        public SampleObject read(String key) {
            throw new IllegalStateException();
        }

        @Cacheable(cacheNames = "testMap", key = "#key")
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
            return BaseTest.createInstance();
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) throws IOException {
            Map<String, CacheConfig> config = new HashMap<String, CacheConfig>();
            config.put("testMap", new CacheConfig(24 * 60 * 1000, 12 * 60 * 1000));
            return new RedissonSpringCacheManager(redissonClient, config);
        }

    }

    @Configuration
    @ComponentScan
    @EnableCaching
    public static class JsonConfigApplication {

        @Bean(destroyMethod = "shutdown")
        RedissonClient redisson() {
            return BaseTest.createInstance();
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) throws IOException {
            return new RedissonSpringCacheManager(redissonClient, "classpath:/org/redisson/spring/cache/cache-config.json");
        }

    }

    private static Map<Class<?>, AnnotationConfigApplicationContext> contexts;

    public static List<Class<?>> data() {
        return Arrays.asList(Application.class, JsonConfigApplication.class);
    }

    @BeforeAll
    public static void before() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner.startDefaultRedisServerInstance();
        contexts = data().stream().collect(Collectors.toMap(e -> e, e -> new AnnotationConfigApplicationContext(e)));
    }

    @AfterAll
    public static void after() throws InterruptedException {
        contexts.values().forEach(e -> e.close());
        RedisRunner.shutDownDefaultRedisServerInstance();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testNull(Class<?> contextClass) {
        AnnotationConfigApplicationContext context = contexts.get(contextClass);
        SampleBean bean = context.getBean(SampleBean.class);
        bean.store("object1", null);
        assertThat(bean.readNull("object1")).isNull();
        bean.remove("object1");
        assertThat(bean.readNull("object1")).isNull();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRemove(Class<?> contextClass) {
        AnnotationConfigApplicationContext context = contexts.get(contextClass);
        SampleBean bean = context.getBean(SampleBean.class);
        bean.store("object1", new SampleObject("name1", "value1"));
        assertThat(bean.read("object1")).isNotNull();
        bean.remove("object1");
        assertThat(bean.readNull("object1")).isNull();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testPutGet(Class<?> contextClass) {
        AnnotationConfigApplicationContext context = contexts.get(contextClass);
        SampleBean bean = context.getBean(SampleBean.class);
        bean.store("object1", new SampleObject("name1", "value1"));
        SampleObject s = bean.read("object1");
        assertThat(s.getName()).isEqualTo("name1");
        assertThat(s.getValue()).isEqualTo("value1");
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testGet(Class<?> contextClass) {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            AnnotationConfigApplicationContext context = contexts.get(contextClass);
            SampleBean bean = context.getBean(SampleBean.class);
            bean.read("object2");
        });
    }

}
