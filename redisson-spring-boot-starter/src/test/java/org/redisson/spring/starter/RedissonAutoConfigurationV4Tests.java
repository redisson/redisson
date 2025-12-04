/**
 * Copyright (c) 2013-2024 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.spring.starter;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.properties.TestcontainersPropertySourceAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author livk
 */
@SpringJUnitConfig
@Testcontainers(disabledWithoutDocker = true, parallel = true)
@Import({ ServiceConnectionAutoConfiguration.class, TestcontainersPropertySourceAutoConfiguration.class })
class RedissonAutoConfigurationV4Tests {

    @Container
    @ServiceConnection
    static final RedisContainer redis = new RedisContainer("redis:latest");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redisson.config.single-server-config.address",
                () -> "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
        registry.add("spring.redisson.config.codec", () -> "!<org.redisson.codec.JsonJacksonCodec> {}");
    }

    @Autowired
    ConfigurableEnvironment environment;

    final ApplicationContextRunner contextRunner = new ApplicationContextRunner(() -> {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        return context;
    }).withConfiguration(AutoConfigurations.of(RedissonAutoConfiguration.class));

    @Test
    void redissonClient() {
        this.contextRunner.run((context) -> assertThat(context).hasSingleBean(RedissonClient.class));
    }

    @Test
    void fallbackRedissonClient() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.data.redis.host=" + redis.getHost(),
                        "spring.data.redis.port=" + redis.getFirstMappedPort())
                .withConfiguration(AutoConfigurations.of(RedissonAutoConfiguration.class))
                .run((context) -> assertThat(context).hasSingleBean(RedissonClient.class));
    }

    @Test
    void redissonConnectionFactory() {
        contextRunner.run((context) -> assertThat(context).hasSingleBean(RedissonConnectionFactory.class));
    }
}
