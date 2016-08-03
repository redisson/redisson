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
package org.redisson;

import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;

import org.redisson.config.RedissonNodeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonNode {

    private static final Logger log = LoggerFactory.getLogger(RedissonNode.class);
    
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException();
        }
        
        String configPath = args[0];
        RedissonNodeConfig config = null;
        try {
            config = RedissonNodeConfig.fromJSON(new File(configPath));
        } catch (IOException e) {
            // trying next format
            try {
                config = RedissonNodeConfig.fromYAML(new File(configPath));
            } catch (IOException e1) {
                throw new IllegalArgumentException("Can't parse config file " + configPath);
            }
        }
        
        if (config.getExecutors().isEmpty()) {
            throw new IllegalArgumentException("Executor settings are empty");
        }
        
        start(config);
    }

    public static void start(RedissonNodeConfig config) {
        final RedissonClient redisson = Redisson.create(config);
        for (Entry<String, Integer> entry : config.getExecutors().entrySet()) {
            String name = entry.getKey();
            int workers = entry.getValue();
            redisson.getExecutorService(name).registerExecutors(workers);
            log.info("{} worker(s) for '{}' ExecutorService registered", workers, name);
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                redisson.shutdown();
            }
        });
        
        log.info("Redisson node started!");
    }
    
}
