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
package org.redisson.spring.data.connection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.connection.SentinelConnectionManager;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.redis.ExceptionTranslationStrategy;
import org.springframework.data.redis.PassThroughExceptionTranslationStrategy;
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

    private final static Log log = LogFactory.getLog(RedissonConnectionFactory.class);
    
    public static final ExceptionTranslationStrategy EXCEPTION_TRANSLATION = 
                                new PassThroughExceptionTranslationStrategy(new RedissonExceptionConverter());

    private Config config;
    private RedissonClient redisson;
    
    /**
     * Creates factory with default Redisson configuration
     */
    public RedissonConnectionFactory() {
        this(Redisson.create());
    }
    
    /**
     * Creates factory with defined Redisson instance
     * 
     * @param redisson - Redisson instance
     */
    public RedissonConnectionFactory(RedissonClient redisson) {
        this.redisson = redisson;
    }
    
    /**
     * Creates factory with defined Redisson config
     * 
     * @param config - Redisson config
     */
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
    public boolean getConvertPipelineAndTxResults() {
        return true;
    }

    @Override
    public RedisSentinelConnection getSentinelConnection() {
        if (!redisson.getConfig().isSentinelConfig()) {
            throw new InvalidDataAccessResourceUsageException("Redisson is not in Sentinel mode");
        }
        
        SentinelConnectionManager manager = ((SentinelConnectionManager)((Redisson)redisson).getConnectionManager());
        for (RedisClient client : manager.getSentinels()) {
            org.redisson.client.RedisConnection connection = client.connect();
            try {
                String res = connection.sync(RedisCommands.PING);
                if ("pong".equalsIgnoreCase(res)) {
                    return new RedissonSentinelConnection(connection);
                }
            } catch (Exception e) {
                log.warn("Can't connect to " + client, e);
                connection.closeAsync();
            }
        }
        
        throw new InvalidDataAccessResourceUsageException("Sentinels are not found");
    }

}
