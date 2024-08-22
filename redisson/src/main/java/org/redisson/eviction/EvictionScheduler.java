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
package org.redisson.eviction;

import org.redisson.api.MapCacheOptions;
import org.redisson.command.CommandAsyncExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Eviction scheduler.
 * Deletes expired entries in time interval between 5 seconds to 2 hours.
 * It analyzes deleted amount of expired keys
 * and 'tune' next execution delay depending on it.
 *
 * @author Nikita Koksharov
 *
 */
public class EvictionScheduler {

    private final Map<String, EvictionTask> tasks = new ConcurrentHashMap<>();
    private final CommandAsyncExecutor executor;

    public EvictionScheduler(CommandAsyncExecutor executor) {
        this.executor = executor;
    }

    private void addTask(String name, Supplier<EvictionTask> supplier) {
        tasks.computeIfAbsent(name, k -> {
            EvictionTask task = supplier.get();
            task.schedule();
            return task;
        });
    }

    public void scheduleCleanMultimap(String name, String timeoutSetName) {
        addTask(name, () -> new MultimapEvictionTask(name, timeoutSetName, executor));
    }

    public void scheduleJCache(String name, String timeoutSetName, String expiredChannelName) {
        addTask(name, () -> new JCacheEvictionTask(name, timeoutSetName, expiredChannelName, executor));
    }

    public void scheduleTimeSeries(String name, String timeoutSetName) {
        addTask(name, () -> new TimeSeriesEvictionTask(name, timeoutSetName, executor));
    }

    public void schedule(String name, long shiftInMilliseconds) {
        addTask(name, () -> new ScoredSetEvictionTask(name, executor, shiftInMilliseconds));
    }

    public void schedule(String name, String timeoutSetName, String maxIdleSetName,
                         String expiredChannelName, String lastAccessTimeSetName, MapCacheOptions<?, ?> options,
                         String publishCommand) {
        boolean removeEmpty;
        if (options != null) {
            removeEmpty = options.isRemoveEmptyEvictionTask();
        } else {
            removeEmpty = false;
        }

        addTask(name, () -> new MapCacheEvictionTask(name, timeoutSetName, maxIdleSetName, expiredChannelName, lastAccessTimeSetName,
                executor, removeEmpty, this, publishCommand));
    }

    public void remove(String name) {
        tasks.computeIfPresent(name, (k, task) -> {
            task.cancel();
            return null;
        });
    }

}
