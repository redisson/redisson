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
package org.redisson.jcache.configuration;

import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> the type of key 
 * @param <V> the type of value
 */
public class RedissonConfiguration<K, V> implements Configuration<K, V> {

    private static final long serialVersionUID = 5331107577281201157L;

    private Configuration<K, V> jcacheConfig;
    
    private Config config;
    private RedissonClient redisson;
    
    RedissonConfiguration(Config config, Configuration<K, V> jcacheConfig) {
        this.config = config;
        this.jcacheConfig = jcacheConfig;
    }
    
    RedissonConfiguration(RedissonClient redisson, Configuration<K, V> jcacheConfig) {
        this.redisson = redisson;
        this.jcacheConfig = jcacheConfig;
    }

    public static <K, V> Configuration<K, V> fromInstance(RedissonClient redisson) {
        MutableConfiguration<K, V> config = new MutableConfiguration<K, V>();
        return fromInstance(redisson, config);
    }
    
    public static <K, V> Configuration<K, V> fromInstance(RedissonClient redisson, Configuration<K, V> jcacheConfig) {
        return new RedissonConfiguration<K, V>(redisson, jcacheConfig);
    }

    public static <K, V> Configuration<K, V> fromConfig(Config config) {
        MutableConfiguration<K, V> jcacheConfig = new MutableConfiguration<K, V>();
        return new RedissonConfiguration<K, V>(config, jcacheConfig);
    }
    
    public static <K, V> Configuration<K, V> fromConfig(Config config, Configuration<K, V> jcacheConfig) {
        return new RedissonConfiguration<K, V>(config, jcacheConfig);
    }
    
    public Configuration<K, V> getJcacheConfig() {
        return jcacheConfig;
    }
    
    public RedissonClient getRedisson() {
        return redisson;
    }
    
    public Config getConfig() {
        return config;
    }
    
    @Override
    public Class<K> getKeyType() {
        return (Class<K>) Object.class;
    }

    @Override
    public Class<V> getValueType() {
        return (Class<V>) Object.class;
    }

    @Override
    public boolean isStoreByValue() {
        return true;
    }

}
