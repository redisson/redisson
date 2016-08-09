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

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonNodeConfig extends Config {

    private int executorServiceThreads = -1;
    private Map<String, Integer> executorServiceWorkers = new HashMap<String, Integer>();
    
    public RedissonNodeConfig() {
        super();
    }

    public RedissonNodeConfig(Config oldConf) {
        super(oldConf);
    }

    public RedissonNodeConfig setExecutorServiceThreads(int executorThreads) {
        this.executorServiceThreads = executorThreads;
        return this;
    }
    public int getExecutorServiceThreads() {
        return executorServiceThreads;
    }
    
    public RedissonNodeConfig setExecutorServiceWorkers(Map<String, Integer> workers) {
        this.executorServiceWorkers = workers;
        return this;
    }
    public Map<String, Integer> getExecutorServiceWorkers() {
        return executorServiceWorkers;
    }
    
    public static RedissonNodeConfig fromJSON(File file) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(file, RedissonNodeConfig.class);
    }
    
    public static RedissonNodeConfig fromYAML(File file) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(file, RedissonNodeConfig.class);
    }
    
}
