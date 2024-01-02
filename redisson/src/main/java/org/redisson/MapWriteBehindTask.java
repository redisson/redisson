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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.api.MapOptions;
import org.redisson.api.RFuture;
import org.redisson.api.RQueue;
import org.redisson.command.CommandAsyncExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MapWriteBehindTask {

    private static final Logger log = LoggerFactory.getLogger(MapWriteBehindTask.class);
    
    private final AtomicBoolean isStarted = new AtomicBoolean();
    private final RQueue<MapWriterTask> writeBehindTasks;
    private final CommandAsyncExecutor commandExecutor;
    private final MapOptions<Object, Object> options;
    
    public MapWriteBehindTask(String name, CommandAsyncExecutor commandExecutor, MapOptions<?, ?> options) {
        super();
        this.commandExecutor = commandExecutor;
        this.options = (MapOptions<Object, Object>) options;
        String queueName = RedissonObject.suffixName(name, "write-behind-queue");
        this.writeBehindTasks = new RedissonQueue<>(commandExecutor, queueName, null);
    }

    public void start() {
        if (!isStarted.compareAndSet(false, true)) {
            return;
        }

        enqueueTask();
    }

    private void pollTask(Map<Object, Object> addedMap, List<Object> deletedKeys) {
        RFuture<MapWriterTask> future = writeBehindTasks.pollAsync();
        future.whenComplete((task, e) -> {
            if (e != null) {
                log.error(e.getMessage(), e);

                enqueueTask();
                return;
            }

            commandExecutor.getServiceManager().getExecutor().execute(() -> {
                if (task != null) {
                    processTask(addedMap, deletedKeys, task);
                    pollTask(addedMap, deletedKeys);
                } else {
                    flushTasks(addedMap, deletedKeys);
                    enqueueTask();
                }
            });
        });
    }

    private void flushTasks(Map<Object, Object> addedMap, List<Object> deletedKeys) {
        try {
            if (!deletedKeys.isEmpty()) {
                if (options.getWriter() != null) {
                    options.getWriter().delete(deletedKeys);
                } else {
                    options.getWriterAsync().delete(deletedKeys).toCompletableFuture().join();
                }
                deletedKeys.clear();
            }
        } catch (Exception exception) {
            log.error("Unable to delete keys: {}", deletedKeys, exception);
        }
        try {
            if (!addedMap.isEmpty()) {
                if (options.getWriter() != null) {
                    options.getWriter().write(addedMap);
                } else {
                    options.getWriterAsync().write(addedMap).toCompletableFuture().join();
                }
                addedMap.clear();
            }
        } catch (Exception exception) {
            log.error("Unable to add keys: {}", addedMap, exception);
        }
    }

    private void processTask(Map<Object, Object> addedMap, List<Object> deletedKeys, MapWriterTask task) {
        if (task instanceof MapWriterTask.Remove) {
            for (Object key : task.getKeys()) {
                try {
                    deletedKeys.add(key);
                    if (deletedKeys.size() == options.getWriteBehindBatchSize()) {
                        if (options.getWriter() != null) {
                            options.getWriter().delete(deletedKeys);
                        } else {
                            options.getWriterAsync().delete(deletedKeys).toCompletableFuture().join();
                        }
                        deletedKeys.clear();

                    }
                } catch (Exception exception) {
                    log.error("Unable to delete keys: {}", deletedKeys, exception);
                }
            }
        } else {
            for (Entry<Object, Object> entry : task.getMap().entrySet()) {
                try {
                    addedMap.put(entry.getKey(), entry.getValue());
                    if (addedMap.size() == options.getWriteBehindBatchSize()) {
                        if (options.getWriter() != null) {
                            options.getWriter().write(addedMap);
                        } else {
                            options.getWriterAsync().write(addedMap).toCompletableFuture().join();
                        }
                        addedMap.clear();
                    }
                } catch (Exception exception) {
                    log.error("Unable to add keys: {}", addedMap, exception);
                }
            }
        }
    }

    private void enqueueTask() {
        if (!isStarted.get()) {
            return;
        }

        commandExecutor.getServiceManager().newTimeout(t -> {
            if (!isStarted.get()) {
                return;
            }

            Map<Object, Object> addedMap = new LinkedHashMap<>();
            List<Object> deletedKeys = new ArrayList<>();
            pollTask(addedMap, deletedKeys);
        }, options.getWriteBehindDelay(), TimeUnit.MILLISECONDS);
    }

    public void addTask(MapWriterTask task) {
        writeBehindTasks.addAsync(task);
    }

    public void stop() {
        isStarted.set(false);

        Map<Object, Object> addedMap = new LinkedHashMap<>();
        List<Object> deletedKeys = new ArrayList<>();
        for (MapWriterTask task : writeBehindTasks.readAll()) {
            processTask(addedMap, deletedKeys, task);
        }
        flushTasks(addedMap, deletedKeys);
    }
}
