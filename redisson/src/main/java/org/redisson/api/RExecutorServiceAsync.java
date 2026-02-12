/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Distributed async implementation of {@link java.util.concurrent.ExecutorService}
 * 
 * @author Nikita Koksharov
 *
 */
public interface RExecutorServiceAsync {

    /**
     * Returns <code>true</code> if this Executor Service has task
     * by <code>taskId</code> awaiting execution or currently in execution
     *
     * @param taskId id of task
     * @return <code>true</code> if this Executor Service has task
     */
    RFuture<Boolean> hasTaskAsync(String taskId);

    /**
     * Returns amount of tasks awaiting execution or currently in execution.
     *
     * @return amount of tasks
     */
    RFuture<Integer> getTaskCountAsync();

    /**
     * Returns list of task ids awaiting execution or currently in execution.
     *
     * @return task ids
     */
    RFuture<Set<String>> getTaskIdsAsync();

    /**
     * Cancel task by id
     *
     * @see RExecutorFuture#getTaskId()
     *
     * @param taskId id of task
     * @return <code>true</code> if task has been canceled successfully
     *          or <code>null</code> if task wasn't found
     */
    RFuture<Boolean> cancelTaskAsync(String taskId);

    /**
     * Deletes executor request queue and state objects
     * 
     * @return <code>true</code> if any of objects were deleted
     */
    RFuture<Boolean> deleteAsync();

    /**
     * Submits task for execution asynchronously  
     * 
     * @param <T> type of return value
     * @param task task to execute
     * @return Future object
     */
    <T> RExecutorFuture<T> submitAsync(Callable<T> task);

    /**
     * Synchronously submits a value-returning task
     * with specified id for execution asynchronously.
     * Returns a Future representing the pending results of the task.
     *
     * @param id task id
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @return a Future representing pending completion of the task
     */
    <T> RExecutorFuture<T> submitAsync(String id, Callable<T> task);

    /**
     * Submits a value-returning task with defined <code>timeToLive</code> parameter
     * for execution asynchronously. Returns a Future representing the pending
     * results of the task. The Future's {@code get} method will return the
     * task's result upon successful completion.
     *
     * @param task the task to submit
     * @param timeToLive time to live interval
     * @param timeUnit unit of time to live interval
     * @param <T> the type of the task's result
     * @return a Future representing pending completion of the task
     */
    <T> RExecutorFuture<T> submitAsync(Callable<T> task, long timeToLive, TimeUnit timeUnit);

    /**
     * Synchronously submits a value-returning task with
     * defined <code>id</code> and <code>timeToLive</code> parameters
     * for execution asynchronously.
     * Returns a Future representing the pending results of the task.
     *
     * @param id task id
     * @param task the task to submit
     * @param timeToLive time to live interval
     * @param <T> the type of the task's result
     * @return a Future representing pending completion of the task
     */
    <T> RExecutorFuture<T> submitAsync(String id, Callable<T> task, Duration timeToLive);

    /**
     * Submits tasks batch for execution asynchronously.
     * All tasks are stored to executor request queue atomically,
     * if case of any error none of tasks will be added.
     * 
     * @param tasks tasks to execute
     * @return Future object
     */
    RExecutorBatchFuture submitAsync(Callable<?>... tasks);
    
    /**
     * Submits task for execution asynchronously
     * 
     * @param task - task to execute
     * @return Future object
     */
    RExecutorFuture<?> submitAsync(Runnable task);

    /**
     * Synchronously submits a Runnable task with id for execution asynchronously.
     * Returns a RExecutorFuture representing task completion.
     *
     * @param id task id
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     */
    RExecutorFuture<?> submitAsync(String id, Runnable task);
    
    /**
     * Submits a task with defined <code>timeToLive</code> parameter
     * for execution asynchronously. Returns a Future representing task completion.
     * The Future's {@code get} method will return the
     * task's result upon successful completion.
     *
     * @param task the task to submit
     * @param timeToLive time to live interval
     * @param timeUnit unit of time to live interval
     * @return a Future representing pending completion of the task
     */
    RExecutorFuture<?> submitAsync(Runnable task, long timeToLive, TimeUnit timeUnit);

    /**
     * Synchronously submits a task
     * with defined <code>id</code> and <code>timeToLive</code> parameters
     * for execution asynchronously.
     * Returns a Future representing task completion.
     *
     * @param id task id
     * @param task the task to submit
     * @param timeToLive time to live interval
     * @return a Future representing pending completion of the task
     */
    RExecutorFuture<?> submitAsync(String id, Runnable task, Duration timeToLive);

    /**
     * Submits tasks batch for execution asynchronously. All tasks are stored to executor request queue atomically, 
     * if case of any error none of tasks will be added.
     * 
     * @param tasks tasks to execute
     * @return Future object
     */
    RExecutorBatchFuture submitAsync(Runnable... tasks);
    
}
