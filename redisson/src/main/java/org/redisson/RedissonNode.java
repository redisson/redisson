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
import java.net.InetSocketAddress;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnection;
import org.redisson.config.RedissonNodeConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.RedissonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ThreadLocalRandom;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonNode {

    private static final Logger log = LoggerFactory.getLogger(RedissonNode.class);
    
    private ExecutorService executor;
    private RedissonClient redisson;
    private final RedissonNodeConfig config;
    private final String id;
    private InetSocketAddress remoteAddress;
    private InetSocketAddress localAddress;
    
    private RedissonNode(RedissonNodeConfig config) {
        this.config = new RedissonNodeConfig(config);
        this.id = generateId();
    }
    
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }
    
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }
    
    public String getId() {
        return id;
    }
    
    private String generateId() {
        byte[] id = new byte[8];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        ThreadLocalRandom.current().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Config file not defined");
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
                log.error("Can't parse json config " + configPath, e);
                throw new IllegalArgumentException("Can't parse yaml config " + configPath, e1);
            }
        }
        
        final RedissonNode node = RedissonNode.create(config);
        node.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                node.shutdown();
            }
        });
    }
    
    /**
     * Shutdown Redisson node instance
     * 
     */
    public void shutdown() {
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
        redisson.shutdown();
        log.info("Redisson node has been shutdown successfully");
    }
    
    /**
     * Start Redisson node instance
     */
    public void start() {
        if (config.getExecutorServiceThreads() == 0) {
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2, new RedissonThreadFactory());
        } else if (config.getExecutorServiceThreads() > 0) {
            executor = Executors.newFixedThreadPool(config.getExecutorServiceThreads(), new RedissonThreadFactory());
        }

        redisson = Redisson.create(config);
        
        retrieveAdresses();
        
        if (config.getRedissonNodeInitializer() != null) {
            config.getRedissonNodeInitializer().onStartup(redisson, this);
        }
        
        for (Entry<String, Integer> entry : config.getExecutorServiceWorkers().entrySet()) {
            String name = entry.getKey();
            int workers = entry.getValue();
            redisson.getExecutorService(name).registerWorkers(workers, executor);
            log.info("{} worker(s) for '{}' ExecutorService registered", workers, name);
        }

        log.info("Redisson node started!");
    }

    private void retrieveAdresses() {
        ConnectionManager connectionManager = ((Redisson)redisson).getConnectionManager();
        for (MasterSlaveEntry entry : connectionManager.getEntrySet()) {
            Future<RedisConnection> readFuture = entry.connectionReadOp();
            if (readFuture.awaitUninterruptibly((long)connectionManager.getConfig().getConnectTimeout()) 
                    && readFuture.isSuccess()) {
                RedisConnection connection = readFuture.getNow();
                entry.releaseRead(connection);
                remoteAddress = (InetSocketAddress) connection.getChannel().remoteAddress();
                localAddress = (InetSocketAddress) connection.getChannel().localAddress();
                return;
            }
            Future<RedisConnection> writeFuture = entry.connectionWriteOp();
            if (writeFuture.awaitUninterruptibly((long)connectionManager.getConfig().getConnectTimeout())
                    && writeFuture.isSuccess()) {
                RedisConnection connection = writeFuture.getNow();
                entry.releaseWrite(connection);
                remoteAddress = (InetSocketAddress) connection.getChannel().remoteAddress();
                localAddress = (InetSocketAddress) connection.getChannel().localAddress();
                return;
            }
        }
    }

    /**
     * Create Redisson node instance with provided config
     *
     * @param config
     * @return RedissonNode instance
     */
    public static RedissonNode create(RedissonNodeConfig config) {
        if (config.getExecutorServiceWorkers().isEmpty()) {
            throw new IllegalArgumentException("Executor service workers are empty");
        }
        
        return new RedissonNode(config);
    }
    
}
