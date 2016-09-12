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

import java.util.concurrent.Callable;

/**
 * Distributed implementation of {@link java.util.concurrent.ExecutorService}
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
     * Submit task for execution in async mode with listeners support 
     * 
     * @param <T> type of return value
     * @param task - task to execute
     * @return Future object
     */
    <T> RFuture<T> submitAsync(Callable<T> task);

    /**
     * Submit task for execution in async mode with listeners support
     * 
     * @param task - task to execute
     * @return Future object
     */
    RFuture<?> submitAsync(Runnable task);
    
}
