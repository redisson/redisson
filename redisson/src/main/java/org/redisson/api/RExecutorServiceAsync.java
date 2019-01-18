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
package org.redisson.api;

import java.util.concurrent.Callable;

/**
 * Distributed async implementation of {@link java.util.concurrent.ExecutorService}
 * 
 * @author Nikita Koksharov
 *
 */
public interface RExecutorServiceAsync {

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
     * @param task - task to execute
     * @return Future object
     */
    <T> RExecutorFuture<T> submitAsync(Callable<T> task);

    /**
     * Submits tasks batch for execution asynchronously. All tasks are stored to executor request queue atomically, 
     * if case of any error none of tasks will be added.
     * 
     * @param tasks - tasks to execute
     * @return Future object
     */
    RExecutorBatchFuture submitAsync(Callable<?> ...tasks);
    
    /**
     * Submits task for execution asynchronously
     * 
     * @param task - task to execute
     * @return Future object
     */
    RExecutorFuture<?> submitAsync(Runnable task);
    
    /**
     * Submits tasks batch for execution asynchronously. All tasks are stored to executor request queue atomically, 
     * if case of any error none of tasks will be added.
     * 
     * @param tasks - tasks to execute
     * @return Future object
     */
    RExecutorBatchFuture submitAsync(Runnable ...tasks);
    
}
