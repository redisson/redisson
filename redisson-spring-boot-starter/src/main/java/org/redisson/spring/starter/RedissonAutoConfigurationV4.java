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

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.redisson.misc.RedisURI;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.CustomEditorConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Spring configuration used with Spring Boot 4.0+
 *
 * @author Nikita Koksharov
 *
 */
@AutoConfiguration(before = DataRedisAutoConfiguration.class)
@ConditionalOnClass({Redisson.class, RedisOperations.class, DataRedisAutoConfiguration.class})
@EnableConfigurationProperties({RedissonPropertiesV4.class, DataRedisProperties.class})
public class RedissonAutoConfigurationV4 {

    @Bean
    public CustomEditorConfigurer customEditorConfigurer() {
        CustomEditorConfigurer configurer = new CustomEditorConfigurer();
        configurer.setPropertyEditorRegistrars(new PropertyEditorRegistrar[] { new RedissonPropertyEditorRegistrar() });
        return configurer;
    }

    /**
     * RedissonClient
     * @param properties the config properties
     * @param configCustomizers the config customizers
     * @return the redisson client
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @Conditional(RedissonCondition.class)
    public RedissonClient redissonClient(RedissonPropertiesV4 properties,
                                         ObjectProvider<RedissonAutoConfigurationCustomizer> configCustomizers) {
        return RedissonClientFactory.create(properties, configCustomizers);
    }

    @Fallback
    @Bean(value = "redissonClient", destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient fallbackRedissonClient(DataRedisProperties properties,
                                                 ObjectProvider<RedissonAutoConfigurationCustomizer> configCustomizers) {
        return RedissonClientFactory.create(properties, configCustomizers);
    }

    /**
     * Redisson connection factory redisson connection factory.
     * @param redisson the redisson
     * @return the redisson connection factory
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(RedissonConnectionFactory.class)
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
        return new RedissonConnectionFactory(redisson);
    }

}
