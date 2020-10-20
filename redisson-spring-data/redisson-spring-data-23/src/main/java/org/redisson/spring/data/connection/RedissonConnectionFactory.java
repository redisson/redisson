/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
import org.redisson.RedissonKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.connection.SentinelConnectionManager;
import org.redisson.reactive.CommandReactiveService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.redis.ExceptionTranslationStrategy;
import org.springframework.data.redis.PassThroughExceptionTranslationStrategy;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
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
public class RedissonConnectionFactory implements RedisConnectionFactory, 
                ReactiveRedisConnectionFactory, InitializingBean, DisposableBean {

    private final static Log log = LogFactory.getLog(RedissonConnectionFactory.class);
    
    public static final ExceptionTranslationStrategy EXCEPTION_TRANSLATION = 
                                new PassThroughExceptionTranslationStrategy(new RedissonExceptionConverter());

    private Config config;
    private RedissonClient redisson;
    private boolean hasOwnRedisson;

    /**
     * Creates factory with default Redisson configuration
     */
    public RedissonConnectionFactory() {
        this(Redisson.create());
        hasOwnRedisson = true;
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
        hasOwnRedisson = true;
    }

    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        return EXCEPTION_TRANSLATION.translate(ex);
    }

    @Override
    public void destroy() throws Exception {
        if (hasOwnRedisson) {
            redisson.shutdown();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (config != null) {
            redisson = Redisson.create(config);
        }
    }

    @Override
    public RedisConnection getConnection() {
        if (redisson.getConfig().isClusterConfig()) {
            return new RedissonClusterConnection(redisson);
        }
        return new RedissonConnection(redisson);
    }

    @Override
    public RedisClusterConnection getClusterConnection() {
        if (!redisson.getConfig().isClusterConfig()) {
            throw new InvalidDataAccessResourceUsageException("Redisson is not in Cluster mode");
        }
        return new RedissonClusterConnection(redisson);
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
        
        SentinelConnectionManager manager = ((SentinelConnectionManager)((RedissonKeys)redisson.getKeys()).getConnectionManager());
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

    @Override
    public ReactiveRedisConnection getReactiveConnection() {
        return new RedissonReactiveRedisConnection(new CommandReactiveService(((RedissonKeys)redisson.getKeys()).getConnectionManager()));
    }

    @Override
    public ReactiveRedisClusterConnection getReactiveClusterConnection() {
        return new RedissonReactiveRedisClusterConnection(new CommandReactiveService(((RedissonKeys)redisson.getKeys()).getConnectionManager()));
    }

}
