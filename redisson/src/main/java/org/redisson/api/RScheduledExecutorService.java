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
package org.redisson.api;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.redisson.CronSchedule;

/**
 * Distributed implementation of {@link java.util.concurrent.ScheduledExecutorService}
 * 
 * @author Nikita Koksharov
 *
 */
public interface RScheduledExecutorService extends RExecutorService, ScheduledExecutorService, RScheduledExecutorServiceAsync {

    /**
     * Cancels scheduled task by id
     * 
     * @see RScheduledFuture#getTaskId()
     * 
     * @param taskId - id of task
     * @return <code>true</code> if task has been canceled successfully
     */
    boolean cancelScheduledTask(String taskId);
    
    /**
     * Creates and executes a periodic action with cron schedule object.
     * If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.  If any execution of this task
     * takes longer than its period, then subsequent executions
     * may start late, but will not concurrently execute.
     *
     * @param task - command the task to execute
     * @param cronSchedule- cron schedule object
     * @return future object
     */
    ScheduledFuture<?> schedule(Runnable task, CronSchedule cronSchedule);
    
}
