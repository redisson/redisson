/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.redisson.api.MapOptions;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class WriteBehindService {

    private final ConcurrentMap<String, MapWriteBehindTask> tasks = new ConcurrentHashMap<>();
    private final CommandAsyncExecutor executor;

    public WriteBehindService(CommandAsyncExecutor executor) {
        this.executor = executor;
    }

    public MapWriteBehindTask start(String name, MapOptions<?, ?> options) {
        MapWriteBehindTask task = tasks.get(name);
        if (task != null) {
            return task;
        }
        
        task = new MapWriteBehindTask(name, executor, options);
        MapWriteBehindTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask != null) {
            task = prevTask;
        }
        task.start();
        return task;
    }

    public void stop() {
        tasks.values().forEach(t -> t.stop());
    }

    public void stop(String name) {
        MapWriteBehindTask task = tasks.remove(name);
        task.stop();
    }

}
