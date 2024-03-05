package org.redisson.spring.transaction;

import org.redisson.RedisDockerTest;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.PreDestroy;

@Configuration
@EnableTransactionManagement
public class RedissonReactiveTransactionContextConfig {

    @Bean
    public ReactiveTransactionalBean2 transactionalBean2() {
        return new ReactiveTransactionalBean2();
    }

    @Bean
    public ReactiveTransactionalBean transactionBean() {
        return new ReactiveTransactionalBean();
    }
    
    @Bean
    public ReactiveRedissonTransactionManager transactionManager(RedissonReactiveClient redisson) {
        return new ReactiveRedissonTransactionManager(redisson);
    }

    @Bean
    public RedissonReactiveClient redisson() {
        return RedisDockerTest.createInstance().reactive();
    }
    
    @PreDestroy
    public void destroy() {
        redisson().shutdown();
    }
}
