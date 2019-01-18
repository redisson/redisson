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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.MapSession;

/**
 * Enables Redisson's Spring Session implementation backed by Redis and
 * exposes SessionRepositoryFilter as a bean named "springSessionRepositoryFilter".
 * <p>
 * Redisson instance should be registered as bean in application context.
 * Usage example:
 * <pre>
 * <code>
 * {@literal @Configuration}
 * {@literal EnableRedissonHttpSession}
 * public class RedissonHttpSessionConfig {
 *    
 *    {@literal @Bean}
 *    public RedissonClient redisson() {
 *        return Redisson.create();
 *    }
 *    
 * }
 * </code>
 * </pre>
 * 
 * @author Nikita Koksharov
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(RedissonHttpSessionConfiguration.class)
@Configuration
public @interface EnableRedissonHttpSession {

    int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;
    
    String keyPrefix() default "";
    
}
