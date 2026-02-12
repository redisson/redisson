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

import java.util.concurrent.TimeUnit;

/**
 * Configuration for ExecutorService.
 * 
 * @author Nikita Koksharov
 *
 */
@Deprecated
public final class ExecutorOptions {
    
    private long taskRetryInterval = 5 * 60000;

    private IdGenerator idGenerator = IdGenerator.random();

    private ExecutorOptions() {
    }
    
    public static ExecutorOptions defaults() {
        return new ExecutorOptions();
    }
    
    public long getTaskRetryInterval() {
        return taskRetryInterval;
    }
    
    /**
     * Defines task retry interval at the end of which task
     * is executed again by ExecutorService worker.
     * <p>
     * Counted from the task start moment.
     * Applied only if the task was in progress but for some reason
     * wasn't marked as completed (successful or unsuccessful).
     * <p>
     * Set <code>0</code> to disable.
     * <p>
     * Default is <code>5 minutes</code>
     * 
     * @param timeout value
     * @param unit value
     * @return self instance
     */
    public ExecutorOptions taskRetryInterval(long timeout, TimeUnit unit) {
        this.taskRetryInterval = unit.toMillis(timeout);
        return this;
    }

    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    /**
     * Defines identifier generator
     *
     * @param idGenerator identifier generator
     * @return self instance
     */
    public ExecutorOptions idGenerator(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
        return this;
    }

}
