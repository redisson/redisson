package org.redisson.spring.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;

@SpringBootApplication
public class RedissonRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedissonRestApplication.class, args);
    }

}
