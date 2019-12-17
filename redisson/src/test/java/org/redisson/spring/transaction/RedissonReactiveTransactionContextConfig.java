package org.redisson.spring.transaction;

import org.redisson.BaseTest;
import org.redisson.Redisson;
import org.redisson.RedissonReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.transaction.operation.TransactionalOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.reactive.TransactionalOperator;

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
        return Redisson.createReactive(BaseTest.createConfig());
    }
    
    @PreDestroy
    public void destroy() {
        redisson().shutdown();
    }
}
