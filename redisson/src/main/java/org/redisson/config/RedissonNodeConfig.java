/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.redisson.api.RedissonNodeInitializer;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonNodeConfig extends Config {
    
    private RedissonNodeInitializer redissonNodeInitializer;
    private Map<String, Integer> executorServiceWorkers = new HashMap<String, Integer>();
    
    public RedissonNodeConfig() {
        super();
    }

    public RedissonNodeConfig(Config oldConf) {
        super(oldConf);
    }
    
    public RedissonNodeConfig(RedissonNodeConfig oldConf) {
        super(oldConf);
        this.executorServiceWorkers = new HashMap<String, Integer>(oldConf.executorServiceWorkers);
        this.redissonNodeInitializer = oldConf.redissonNodeInitializer;
    }
    
    /**
     * Executor service workers amount per service name 
     * 
     * @param workers
     * @return
     */
    public RedissonNodeConfig setExecutorServiceWorkers(Map<String, Integer> workers) {
        this.executorServiceWorkers = workers;
        return this;
    }
    public Map<String, Integer> getExecutorServiceWorkers() {
        return executorServiceWorkers;
    }
    
    public RedissonNodeInitializer getRedissonNodeInitializer() {
        return redissonNodeInitializer;
    }

    /**
     * Redisson node initializer
     * 
     * @param redissonNodeInitializer
     * @return
     */
    public RedissonNodeConfig setRedissonNodeInitializer(RedissonNodeInitializer redissonNodeInitializer) {
        this.redissonNodeInitializer = redissonNodeInitializer;
        return this;
    }

    /**
     * Read config object stored in JSON format from <code>File</code>
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static RedissonNodeConfig fromJSON(File file) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(file, RedissonNodeConfig.class);
    }

    /**
     * Read config object stored in YAML format from <code>File</code>
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static RedissonNodeConfig fromYAML(File file) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(file, RedissonNodeConfig.class);
    }
    
}
