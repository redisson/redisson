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
package org.redisson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.api.MapOptions;
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
    
    private final AtomicBoolean isScheduled = new AtomicBoolean();
    private final Queue<MapWriterTask> writeBehindTasks = new ConcurrentLinkedQueue<>();
    private final CommandAsyncExecutor commandExecutor;
    private final MapOptions<Object, Object> options;
    
    public MapWriteBehindTask(CommandAsyncExecutor commandExecutor, MapOptions<?, ?> options) {
        super();
        this.commandExecutor = commandExecutor;
        this.options = (MapOptions<Object, Object>) options;
    }

    private void enqueueTask() {
        if (!isScheduled.compareAndSet(false, true)) {
            return;
        }
        
        commandExecutor.getConnectionManager().newTimeout(t -> {
            commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                Map<Object, Object> addedMap = new LinkedHashMap<>();
                List<Object> deletedKeys = new ArrayList<>();
                try {
                    while (true) {
                        MapWriterTask task = writeBehindTasks.poll();
                        if (task == null) {
                            break;
                        }
                        
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
                    }
                    
                    if (!deletedKeys.isEmpty()) {
                        options.getWriter().delete(deletedKeys);
                        deletedKeys.clear();
                    }
                    if (!addedMap.isEmpty()) {
                        options.getWriter().write(addedMap);
                        addedMap.clear();
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

                isScheduled.set(false);
                if (!writeBehindTasks.isEmpty()) {
                    enqueueTask();
                }
            });
        }, options.getWriteBehindDelay(), TimeUnit.MILLISECONDS);
    }

    public void addTask(MapWriterTask task) {
        writeBehindTasks.add(task);

        enqueueTask();
    }
    
}
