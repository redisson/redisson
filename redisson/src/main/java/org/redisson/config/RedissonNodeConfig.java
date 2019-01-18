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
package org.redisson.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.redisson.api.RedissonNodeInitializer;

/**
 * Redisson Node configuration
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonNodeConfig extends Config {
    
    private int mapReduceWorkers = 0;
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
     * MapReduce workers amount. 
     * <p>
     * <code>0 = current_processors_amount</code>
     * <p>
     * <code>-1 = disable MapReduce workers</code>
     * 
     * <p>
     * Default is <code>0</code>
     * 
     * @param mapReduceWorkers workers for MapReduce
     * @return config
     */
    public RedissonNodeConfig setMapReduceWorkers(int mapReduceWorkers) {
        this.mapReduceWorkers = mapReduceWorkers;
        return this;
    }
    public int getMapReduceWorkers() {
        return mapReduceWorkers;
    }
    
    /**
     * Executor service workers amount per service name 
     * 
     * @param workers mapping
     * @return config
     */
    public RedissonNodeConfig setExecutorServiceWorkers(Map<String, Integer> workers) {
        this.executorServiceWorkers = workers;
        return this;
    }
    public Map<String, Integer> getExecutorServiceWorkers() {
        return executorServiceWorkers;
    }
    
    /**
     * Redisson node initializer
     * 
     * @param redissonNodeInitializer object
     * @return config
     */
    public RedissonNodeConfig setRedissonNodeInitializer(RedissonNodeInitializer redissonNodeInitializer) {
        this.redissonNodeInitializer = redissonNodeInitializer;
        return this;
    }
    public RedissonNodeInitializer getRedissonNodeInitializer() {
        return redissonNodeInitializer;
    }


    /**
     * Read config object stored in JSON format from <code>File</code>
     *
     * @param file object
     * @return config
     * @throws IOException error
     */
    public static RedissonNodeConfig fromJSON(File file) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(file, RedissonNodeConfig.class);
    }

    /**
     * Read config object stored in YAML format from <code>File</code>
     *
     * @param file object
     * @return config
     * @throws IOException error
     */
    public static RedissonNodeConfig fromYAML(File file) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(file, RedissonNodeConfig.class);
    }
    
}
