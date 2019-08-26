package org.redisson.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.Callable;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.RedissonNode;
import org.redisson.api.RExecutorFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.redisson.config.Config;
import org.redisson.config.RedissonNodeConfig;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

public class RedissonExecutorServiceSpringTest extends BaseTest {

    public static class SampleRunnable implements Runnable, Serializable {

        @Autowired
        private SampleBean bean;
        
        @RInject
        private RedissonClient redisson;
        
        public SampleRunnable() {
        }

        @Override
        public void run() {
            String res = bean.myMethod("runnable");
            redisson.getBucket("result").set(res);
        }

    }
    
    public static class SampleCallable implements Callable<String>, Serializable {

        @Autowired
        private SampleBean bean;
        
        public SampleCallable() {
        }

        @Override
        public String call() throws Exception {
            return bean.myMethod("callable");
        }
        
    }

    @Service
    public static class SampleBean {

        public String myMethod(String key) {
            return "hello " + key;
        }

    }

    private static final String EXECUTOR_NAME = "spring_test";
    
    @Configuration
    @ComponentScan
    public static class Application {

        @Bean(destroyMethod = "shutdown")
        RedissonNode redissonNode(BeanFactory beanFactory) {
            Config config = BaseTest.createConfig();
            RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
            nodeConfig.setExecutorServiceWorkers(Collections.singletonMap(EXECUTOR_NAME, 1));
            nodeConfig.setBeanFactory(beanFactory);
            RedissonNode node = RedissonNode.create(nodeConfig);
            node.start();
            return node;
        }

    }

    private static AnnotationConfigApplicationContext context;
    
    @BeforeClass
    public static void beforeTest() throws FailedToStartRedisException, IOException, InterruptedException {
        context = new AnnotationConfigApplicationContext(Application.class);
    }

    @AfterClass
    public static void afterTest() {
        context.close();
    }

    @Test
    public void testRunnable() throws InterruptedException {
        redisson.getExecutorService(EXECUTOR_NAME).execute(new SampleRunnable());
        
        Thread.sleep(500);

        assertThat(redisson.getBucket("result").get()).isEqualTo("hello runnable");
    }

    @Test
    public void testCallable() throws InterruptedException {
        RExecutorFuture<String> future = redisson.getExecutorService(EXECUTOR_NAME).submit(new SampleCallable());
        
        Thread.sleep(500);

        assertThat(future.sync().getNow()).isEqualTo("hello callable");
    }
    
}
