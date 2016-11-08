package org.redisson.spring.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.redisson.AbstractBaseTest;
import org.redisson.RedisRunner;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@RunWith(Parameterized.class)
public class RedissonSpringCacheTest extends AbstractBaseTest {

    public static class SampleObject {

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
            return redissonRule.createClient();
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
            return redissonRule.createClient();
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) throws IOException {
            return new RedissonSpringCacheManager(redissonClient, "classpath:/org/redisson/spring/cache/cache-config.json");
        }
    }

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Iterable<Object[]> data() throws IOException, InterruptedException {
        return Arrays.asList(new Object[][]{
            {"org.redisson.spring.cache.RedissonSpringCacheTest$Application"},
            {"org.redisson.spring.cache.RedissonSpringCacheTest$JsonConfigApplication"}
        });
    }

    @Parameterized.Parameter(0)
    public String contextStr;
    
    private ApplicationContext context;
    
    @Before
    public void setup() {
        try {
            context = new AnnotationConfigApplicationContext(Class.forName(contextStr));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    
    @After
    public void tearDown() {
        ((ConfigurableApplicationContext) context).close();
    }

    @Test
    public void testNull() {
        SampleBean bean = context.getBean(SampleBean.class);
        bean.store("object1", null);
        assertThat(bean.readNull("object1")).isNull();
        bean.remove("object1");
        assertThat(bean.readNull("object1")).isNull();
    }

    @Test
    public void testRemove() {
        SampleBean bean = context.getBean(SampleBean.class);
        bean.store("object1", new SampleObject("name1", "value1"));
        assertThat(bean.read("object1")).isNotNull();
        bean.remove("object1");
        assertThat(bean.readNull("object1")).isNull();
    }

    @Test
    public void testPutGet() {
        SampleBean bean = context.getBean(SampleBean.class);
        bean.store("object1", new SampleObject("name1", "value1"));
        SampleObject s = bean.read("object1");
        assertThat(s.getName()).isEqualTo("name1");
        assertThat(s.getValue()).isEqualTo("value1");
    }

    @Test(expected = IllegalStateException.class)
    public void testGet() {
        SampleBean bean = context.getBean(SampleBean.class);
        bean.read("object2");
    }
}
