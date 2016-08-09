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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        
        if (config.getExecutorServiceWorkers().isEmpty()) {
            throw new IllegalArgumentException("Executor service workers are empty");
        }
        
        start(config);
    }

    public static void start(RedissonNodeConfig config) {
        final RedissonClient redisson = Redisson.create(config);
        final ExecutorService executor;
        if (config.getExecutorServiceThreads() != -1) {
            if (config.getExecutorServiceThreads() == 0) {
                executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
            } else {
                executor = Executors.newFixedThreadPool(config.getExecutorServiceThreads());
            }
        } else {
            executor = null;
        }
        
        for (Entry<String, Integer> entry : config.getExecutorServiceWorkers().entrySet()) {
            String name = entry.getKey();
            int workers = entry.getValue();
            redisson.getExecutorService(name).registerWorkers(workers, executor);
            log.info("{} worker(s) for '{}' ExecutorService registered", workers, name);
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                redisson.shutdown();
                if (executor != null) {
                    log.info("Worker executor is being shutdown...");
                    executor.shutdown();
                    try {
                        if (executor.awaitTermination(5, TimeUnit.MINUTES)) {
                            log.info("Worker executor has been shutdown successfully");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        
        log.info("Redisson node started!");
    }
    
}
