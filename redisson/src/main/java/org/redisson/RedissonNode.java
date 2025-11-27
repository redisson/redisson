/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBufUtil;
import org.redisson.api.RExecutorService;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RedissonClient;
import org.redisson.api.WorkerOptions;
import org.redisson.client.RedisConnection;
import org.redisson.config.RedissonNodeConfig;
import org.redisson.config.RedissonNodeFileConfig;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.executor.SpringTasksInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public final class RedissonNode {

    private static final Logger log = LoggerFactory.getLogger(RedissonNode.class);

    private final Set<RExecutorService> executors = new HashSet<>();
    private final boolean hasRedissonInstance;
    private RedissonClient redisson;
    private final RedissonNodeConfig config;
    private final String id;
    private InetSocketAddress remoteAddress;
    private InetSocketAddress localAddress;
    
    private RedissonNode(RedissonNodeConfig config, RedissonClient redisson) {
        this.config = new RedissonNodeConfig(config);
        this.id = generateId();
        this.redisson = redisson;
        hasRedissonInstance = redisson == null;
    }

    public RedissonClient getRedisson() {
        return redisson;
    }

    @Deprecated
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    @Deprecated
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }
    
    public String getId() {
        return id;
    }
    
    private String generateId() {
        byte[] id = new byte[8];
        ThreadLocalRandom.current().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Config file not defined");
        }
        
        String configPath = args[0];
        RedissonNodeFileConfig config = null;
        try {
            config = RedissonNodeFileConfig.fromYAML(new File(configPath));
        } catch (IOException e) {
            // trying next format
            try {
                config = RedissonNodeFileConfig.fromJSON(new File(configPath));
            } catch (IOException e1) {
                e1.addSuppressed(e);
                throw new IllegalArgumentException("Can't parse config " + configPath, e1);
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
        if (hasRedissonInstance) {
            try {
                for (RExecutorService executor : executors) {
                    executor.shutdown();
                }
            } catch (Exception e){
                // skip
            }

            redisson.shutdown(0, 15, TimeUnit.SECONDS);
            log.info("Redisson node has been shutdown successfully");
        }
    }
    
    /**
     * Start Redisson node instance
     */
    public void start() {
        if (hasRedissonInstance) {
            redisson = Redisson.create(config);
        }
        
        retrieveAddresses();
        
        if (config.getRedissonNodeInitializer() != null) {
            config.getRedissonNodeInitializer().onStartup(this);
        }
        
        int mapReduceWorkers = config.getMapReduceWorkers();
        if (mapReduceWorkers != -1) {
            if (mapReduceWorkers == 0) {
                mapReduceWorkers = Runtime.getRuntime().availableProcessors();
            }
            
            WorkerOptions options = WorkerOptions.defaults()
                                                .workers(mapReduceWorkers);
            if (config.getBeanFactory() != null) {
                options.tasksInjector(new SpringTasksInjector(config.getBeanFactory()));
            }

            RScheduledExecutorService e = redisson.getExecutorService(RExecutorService.MAPREDUCE_NAME);
            e.registerWorkers(options);
            executors.add(e);
            log.info("{} map reduce worker(s) registered", mapReduceWorkers);
        }
        
        for (Entry<String, Integer> entry : config.getExecutorServiceWorkers().entrySet()) {
            String name = entry.getKey();
            int workers = entry.getValue();

            WorkerOptions options = WorkerOptions.defaults()
                    .workers(mapReduceWorkers);
            if (config.getBeanFactory() != null) {
                options.tasksInjector(new SpringTasksInjector(config.getBeanFactory()));
            }

            RScheduledExecutorService e = redisson.getExecutorService(name);
            e.registerWorkers(options);
            executors.add(e);
            log.info("{} worker(s) registered for ExecutorService with '{}' name", workers, name);
        }

        log.info("Redisson node started!");
    }

    private void retrieveAddresses() {
        ConnectionManager connectionManager = ((Redisson) redisson).getCommandExecutor().getConnectionManager();
        for (MasterSlaveEntry entry : connectionManager.getEntrySet()) {
            CompletionStage<RedisConnection> readFuture = entry.connectionReadOp(null, false);
            RedisConnection readConnection = null;
            try {
                readConnection = readFuture.toCompletableFuture().get(connectionManager.getServiceManager().getConfig().getConnectTimeout(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // skip
            }
            if (readConnection != null) {
                entry.releaseRead(readConnection);
                remoteAddress = (InetSocketAddress) readConnection.getChannel().remoteAddress();
                localAddress = (InetSocketAddress) readConnection.getChannel().localAddress();
                return;
            }

            CompletionStage<RedisConnection> writeFuture = entry.connectionWriteOp(null);
            RedisConnection writeConnection = null;
            try {
                writeConnection = writeFuture.toCompletableFuture().get(connectionManager.getServiceManager().getConfig().getConnectTimeout(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // skip
            }
            if (writeConnection != null) {
                entry.releaseWrite(writeConnection);
                remoteAddress = (InetSocketAddress) writeConnection.getChannel().remoteAddress();
                localAddress = (InetSocketAddress) writeConnection.getChannel().localAddress();
                return;
            }
        }
    }

    /**
     * Create Redisson node instance with provided config
     *
     * @param config of RedissonNode
     * @return RedissonNode instance
     */
    public static RedissonNode create(RedissonNodeConfig config) {
        return create(config, null);
    }

    /**
     * Create Redisson node instance with provided config
     *
     * @param config of RedissonNode
     * @return RedissonNode instance
     */
    public static RedissonNode create(RedissonNodeFileConfig config) {
        return create(new RedissonNodeConfig(config), null);
    }

    /**
     * Create Redisson node instance with provided config and Redisson instance
     *
     * @param config of RedissonNode
     * @param redisson instance
     * @return RedissonNode instance
     */
    public static RedissonNode create(RedissonNodeConfig config, RedissonClient redisson) {
        return new RedissonNode(config, redisson);
    }
    
}
