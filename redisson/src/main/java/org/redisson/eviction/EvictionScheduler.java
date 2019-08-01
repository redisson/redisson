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
package org.redisson.eviction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.redisson.command.CommandAsyncExecutor;

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

    private final ConcurrentMap<String, EvictionTask> tasks = new ConcurrentHashMap<>();
    private final CommandAsyncExecutor executor;

    public EvictionScheduler(CommandAsyncExecutor executor) {
        this.executor = executor;
    }

    public void scheduleCleanMultimap(String name, String timeoutSetName) {
        EvictionTask task = new MultimapEvictionTask(name, timeoutSetName, executor);
        EvictionTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask == null) {
            task.schedule();
        }
    }
    
    public void scheduleJCache(String name, String timeoutSetName, String expiredChannelName) {
        EvictionTask task = new JCacheEvictionTask(name, timeoutSetName, expiredChannelName, executor);
        EvictionTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask == null) {
            task.schedule();
        }
    }
    
    public void schedule(String name, long shiftInMilliseconds) {
        EvictionTask task = new ScoredSetEvictionTask(name, executor, shiftInMilliseconds);
        EvictionTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask == null) {
            task.schedule();
        }
    }

    public void schedule(String name, String timeoutSetName, String maxIdleSetName, String expiredChannelName, String lastAccessTimeSetName) {
        EvictionTask task = new MapCacheEvictionTask(name, timeoutSetName, maxIdleSetName, expiredChannelName, lastAccessTimeSetName, executor);
        EvictionTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask == null) {
            task.schedule();
        }
    }

    public void remove(String name) {
        tasks.remove(name);
    }
    
}
