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
        future.onComplete((task, e) -> {
            if (e != null) {
                log.error(e.getMessage(), e);

                enqueueTask();
                return;
            }

            commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                if (task != null) {
                    if (task instanceof MapWriterTask.Remove) {
                        for (Object key : task.getKeys()) {
                            deletedKeys.add(key);
                            if (deletedKeys.size() == options.getWriteBehindBatchSize()) {
                                options.getWriter().delete(deletedKeys);
                                deletedKeys.clear();
                            }
                        }
                    } else {
                        for (Entry<Object, Object> entry : task.getMap().entrySet()) {
                            addedMap.put(entry.getKey(), entry.getValue());
                            if (addedMap.size() == options.getWriteBehindBatchSize()) {
                                options.getWriter().write(addedMap);
                                addedMap.clear();
                            }
                        }
                    }

                    pollTask(addedMap, deletedKeys);
                } else {
                    if (!deletedKeys.isEmpty()) {
                        options.getWriter().delete(deletedKeys);
                        deletedKeys.clear();
                    }
                    if (!addedMap.isEmpty()) {
                        options.getWriter().write(addedMap);
                        addedMap.clear();
                    }

                    enqueueTask();
                }
            });
        });
    }

    private void enqueueTask() {
        if (!isStarted.get()) {
            return;
        }

        commandExecutor.getConnectionManager().newTimeout(t -> {
            Map<Object, Object> addedMap = new LinkedHashMap<>();
            List<Object> deletedKeys = new ArrayList<>();
            pollTask(addedMap, deletedKeys);
        }, options.getWriteBehindDelay(), TimeUnit.MILLISECONDS);
    }

    public void addTask(MapWriterTask task) {
        writeBehindTasks.add(task);
    }

    public void stop() {
        isStarted.set(false);
    }
}
