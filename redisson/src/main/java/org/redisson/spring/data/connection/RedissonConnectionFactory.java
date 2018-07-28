/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.spring.data.connection;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.ExceptionTranslationStrategy;
import org.springframework.data.redis.PassThroughExceptionTranslationStrategy;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;

/**
 * Redisson based connection factory
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonConnectionFactory implements RedisConnectionFactory, InitializingBean, DisposableBean {

    private static final ExceptionTranslationStrategy EXCEPTION_TRANSLATION = 
                                new PassThroughExceptionTranslationStrategy(new RedissonExceptionConverter());

    private Config config;
    private RedissonClient redisson;
    
    public RedissonConnectionFactory(RedissonClient redisson) {
        this.redisson = redisson;
    }
    
    public RedissonConnectionFactory(Config config) {
        super();
        this.config = config;
    }

    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        return EXCEPTION_TRANSLATION.translate(ex);
    }

    @Override
    public void destroy() throws Exception {
        redisson.shutdown();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (config != null) {
            redisson = Redisson.create(config);
        }
    }

    @Override
    public RedisConnection getConnection() {
        return new RedissonConnection(redisson);
    }

    @Override
    public RedisClusterConnection getClusterConnection() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean getConvertPipelineAndTxResults() {
        return true;
    }

    @Override
    public RedisSentinelConnection getSentinelConnection() {
        // TODO Auto-generated method stub
        return null;
    }

}
