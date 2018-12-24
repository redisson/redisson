package org.redisson.spring.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
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

@RunWith(Parameterized.class)
public class RedissonSpringCacheShortTTLTest {

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
        
        @Cacheable(cacheNames = "testMap", key = "#key", sync = true)
        public SampleObject readNullSync(String key) {
            return null;
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
            return BaseTest.createInstance();
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) throws IOException {
            return new RedissonSpringCacheManager(redissonClient, "classpath:/org/redisson/spring/cache/cache-config-shortTTL.json");
        }

    }

    private static Map<Class<?>, AnnotationConfigApplicationContext> contexts;

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Iterable<Class<?>[]> data() throws IOException, InterruptedException {
        return Arrays.asList(new Class<?>[][]{
            {Application.class},
            {JsonConfigApplication.class}
        });
    }

    @Parameterized.Parameter(0)
    public Class<?> contextClass;
    public AnnotationConfigApplicationContext context;
    
    @Before
    public void dbefore() {
        context = contexts.get(contextClass);
    }
    
    @BeforeClass
    public static void before() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner.startDefaultRedisServerInstance();
        contexts = StreamSupport.stream(RedissonSpringCacheShortTTLTest.data().spliterator(), false)
                          .collect(Collectors.toMap(e -> e[0], e -> new AnnotationConfigApplicationContext(e[0])));
    }

    @Test(expected = IllegalStateException.class)
    public void testPutGet() throws InterruptedException {
        SampleBean bean = context.getBean(SampleBean.class);
        bean.store("object1", new SampleObject("name1", "value1"));
        SampleObject s = bean.read("object1");
        assertThat(s.getName()).isEqualTo("name1");
        assertThat(s.getValue()).isEqualTo("value1");
        
        Thread.sleep(1100);
        
        bean.read("object1");
    }

    
    @Test(expected = IllegalStateException.class)
    public void testPutGetSync() throws InterruptedException {
        SampleBean bean = context.getBean(SampleBean.class);
        bean.readNullSync("object1");
        assertThat(bean.read("object1")).isNull();
        
        Thread.sleep(1100);
        
        bean.read("object1");
    }

}
