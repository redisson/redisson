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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class QueueTransferService {

    private final Map<String, QueueTransferTask> tasks = new ConcurrentHashMap<>();
    
    public void schedule(String name, QueueTransferTask task) {
        QueueTransferTask oldTask = tasks.putIfAbsent(name, task);
        if (oldTask == null) {
            task.start();
        } else {
            oldTask.getLock().execute(() -> {
                oldTask.incUsage();
            });
        }
    }
    
    public void remove(String name) {
        QueueTransferTask task = tasks.get(name);
        if (task == null) {
            return;
        }

        task.getLock().execute(() -> {
            if (task.decUsage() == 0) {
                tasks.remove(name, task);
                task.stop();
            }
        });
    }
    
    

}
