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

import java.util.concurrent.TimeUnit;

/**
 * Configuration for Batch object.
 * 
 * @author Nikita Koksharov
 *
 */
public final class BatchOptions {
    
    public enum ExecutionMode {

        /**
         * Store batched invocations in Redis and execute them atomically as a single command.
         * <p>
         * Please note, that in cluster mode all objects should be on the same cluster slot.
         * https://github.com/antirez/redis/issues/3682 
         * 
         */
        REDIS_READ_ATOMIC,

        /**
         * Store batched invocations in Redis and execute them atomically as a single command.
         * <p>
         * Please note, that in cluster mode all objects should be on the same cluster slot.
         * https://github.com/antirez/redis/issues/3682 
         * 
         */
        REDIS_WRITE_ATOMIC,

        /**
         * Store batched invocations in memory on Redisson side and execute them on Redis.
         * <p>
         * Default mode
         * 
         */
        IN_MEMORY,
        
        /**
         * Store batched invocations on Redisson side and executes them atomically on Redis as a single command.
         * <p>
         * Please note, that in cluster mode all objects should be on the same cluster slot.
         * https://github.com/antirez/redis/issues/3682 
         * 
         */
        IN_MEMORY_ATOMIC,
        
    }
    
    private ExecutionMode executionMode = ExecutionMode.IN_MEMORY;
    
    private long responseTimeout;
    private int retryAttempts;
    private long retryInterval;

    private long syncTimeout;
    private int syncSlaves;
    private boolean skipResult;

    private BatchOptions() {
    }
    
    public static BatchOptions defaults() {
        return new BatchOptions();
    }
    
    public long getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * Defines timeout for Redis response. 
     * Starts to countdown when Redis command has been successfully sent.
     * <p>
     * Default is <code>3000 milliseconds</code>
     * 
     * @param timeout value
     * @param unit value
     * @return self instance
     */
    public BatchOptions responseTimeout(long timeout, TimeUnit unit) {
        this.responseTimeout = unit.toMillis(timeout);
        return this;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    /**
     * Defines attempts amount to send Redis commands batch
     * if it hasn't been sent already.
     * <p>
     * Default is <code>3 attempts</code>
     * 
     * @param retryAttempts value
     * @return self instance
     */
    public BatchOptions retryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
        return this;
    }

    public long getRetryInterval() {
        return retryInterval;
    }
    
    /**
     * Defines time interval for each attempt to send Redis commands batch 
     * if it hasn't been sent already.
     * <p>
     * Default is <code>1500 milliseconds</code>
     * 
     * @param retryInterval - time interval
     * @param retryIntervalUnit - time interval unit
     * @return self instance
     */
    public BatchOptions retryInterval(long retryInterval, TimeUnit retryIntervalUnit) {
        this.retryInterval = retryIntervalUnit.toMillis(retryInterval);
        return this;
    }

    
    /**
     * Synchronize write operations execution within defined timeout 
     * across specified amount of Redis slave nodes.
     * <p>
     * NOTE: Redis 3.0+ required
     * 
     * @param slaves - synchronization timeout
     * @param timeout - synchronization timeout
     * @param unit - synchronization timeout time unit
     * @return self instance
     */
    public BatchOptions syncSlaves(int slaves, long timeout, TimeUnit unit) {
        this.syncSlaves = slaves;
        this.syncTimeout = unit.toMillis(timeout);
        return this;
    }
    public long getSyncTimeout() {
        return syncTimeout;
    }
    public int getSyncSlaves() {
        return syncSlaves;
    }
    
    /**
     * Use {@link #executionMode(ExecutionMode)} setting instead
     * 
     */
    @Deprecated
    public BatchOptions atomic() {
        executionMode = ExecutionMode.IN_MEMORY_ATOMIC;
        return this;
    }
    
    /**
     * Inform Redis not to send reply. This allows to save network traffic for commands with batch with big response.
     * <p>
     * NOTE: Redis 3.2+ required
     * 
     * @return self instance
     */
    public BatchOptions skipResult() {
        skipResult = true;
        return this;
    }
    public boolean isSkipResult() {
        return skipResult;
    }

    /**
     * Sets execution mode.
     * 
     * @see ExecutionMode
     * 
     * @param executionMode - batch execution mode
     * @return self instance
     */
    public BatchOptions executionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
        return this;
    }
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    @Override
    public String toString() {
        return "BatchOptions [queueStore=" + executionMode + "]";
    }
    
    
    
}
