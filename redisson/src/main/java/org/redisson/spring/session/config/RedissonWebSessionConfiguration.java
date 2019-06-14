/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.spring.session.config;

import java.util.Map;

import org.redisson.api.RedissonClient;
import org.redisson.spring.session.ReactiveRedissonSessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.session.config.annotation.web.server.SpringWebSessionConfiguration;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Exposes the {@link WebSessionManager} as the bean
 * named "webSessionManager".
 * <p>
 * Redisson instance should be registered as bean 
 * in application context.
 * 
 * @author Nikita Koksharov
 *
 */
@Configuration
public class RedissonWebSessionConfiguration extends SpringWebSessionConfiguration implements ImportAware {

    private Integer maxInactiveIntervalInSeconds;
    private String keyPrefix;
    
    @Bean
    public ReactiveRedissonSessionRepository sessionRepository(
            RedissonClient redissonClient, ApplicationEventPublisher eventPublisher) {
        ReactiveRedissonSessionRepository repository = new ReactiveRedissonSessionRepository(redissonClient, eventPublisher, keyPrefix);
        repository.setDefaultMaxInactiveInterval(maxInactiveIntervalInSeconds);
        return repository;
    }
    
    public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
        this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
    }
    
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        Map<String, Object> map = importMetadata.getAnnotationAttributes(EnableRedissonWebSession.class.getName());
        AnnotationAttributes attrs = AnnotationAttributes.fromMap(map);
        keyPrefix = attrs.getString("keyPrefix");
        maxInactiveIntervalInSeconds = attrs.getNumber("maxInactiveIntervalInSeconds");
    }
    
}
