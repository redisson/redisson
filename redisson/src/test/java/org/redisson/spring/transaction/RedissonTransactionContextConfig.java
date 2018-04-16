package org.redisson.spring.transaction;

import javax.annotation.PreDestroy;

import org.redisson.BaseTest;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class RedissonTransactionContextConfig {

    @Bean
    public TransactionalBean2 transactionBean2() {
        return new TransactionalBean2();
    }
    
    @Bean
    public TransactionalBean transactionBean() {
        return new TransactionalBean();
    }
    
    @Bean
    public RedissonTransactionManager transactionManager(RedissonClient redisson) {
        return new RedissonTransactionManager(redisson);
    }
    
    @Bean
    public RedissonClient redisson() {
        return BaseTest.createInstance();
    }
    
    @PreDestroy
    public void destroy() {
        redisson().shutdown();
    }
}
