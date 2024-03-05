package org.redisson.spring.transaction;

import org.redisson.RedisDockerTest;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.PreDestroy;

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
        return RedisDockerTest.createInstance();
    }
    
    @PreDestroy
    public void destroy() {
        redisson().shutdown();
    }
}
