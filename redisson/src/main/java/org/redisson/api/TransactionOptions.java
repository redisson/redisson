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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for Transaction.
 * 
 * @author Nikita Koksharov
 *
 */
public final class TransactionOptions {
    
    private long responseTimeout = 3000;
    private int retryAttempts = 3;
    private long retryInterval = 1500;

    private int syncSlaves = 0;
    private long syncTimeout = 5000;
    
    private long timeout = 5000;

    private TransactionOptions() {
    }
    
    public static TransactionOptions defaults() {
        return new TransactionOptions();
    }
    
    public long getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * Defines timeout for Redis response. 
     * Starts to countdown when transaction has been successfully sent.
     * <p>
     * Default is <code>3000 milliseconds</code>
     * 
     * @param timeout value
     * @param unit value
     * @return self instance
     */
    public TransactionOptions responseTimeout(long timeout, TimeUnit unit) {
        this.responseTimeout = unit.toMillis(timeout);
        return this;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    /**
     * Defines attempts amount to send transaction
     * if it hasn't been sent already.
     * <p>
     * Default is <code>3 attempts</code>
     * 
     * @param retryAttempts value
     * @return self instance
     */
    public TransactionOptions retryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
        return this;
    }

    public long getRetryInterval() {
        return retryInterval;
    }
    
    /**
     * Defines time interval for each attempt to send transaction 
     * if it hasn't been sent already.
     * <p>
     * Default is <code>1500 milliseconds</code>
     * 
     * @param retryInterval time interval
     * @param retryIntervalUnit time interval unit
     * @return self instance
     */
    public TransactionOptions retryInterval(long retryInterval, TimeUnit retryIntervalUnit) {
        this.retryInterval = retryIntervalUnit.toMillis(retryInterval);
        return this;
    }

    /**
     * Use {@link #syncSlaves} method instead.
     *
     * @param syncTimeout synchronization timeout
     * @param syncUnit synchronization timeout time unit
     * @return self instance
     */
    @Deprecated
    public TransactionOptions syncSlavesTimeout(long syncTimeout, TimeUnit syncUnit) {
        this.syncTimeout = syncUnit.toMillis(syncTimeout);
        return this;
    }
    public long getSyncTimeout() {
        return syncTimeout;
    }

    /**
     * Synchronize write operations execution within defined timeout
     * across specified amount of Redis slave nodes.
     * <p>
     * Default slaves value is <code>0</code> which means available slaves
     * at the moment of execution and <code>-1</code> means no sync at all.
     * <p>
     * Default timeout value is <code>5000 milliseconds</code>
     * NOTE: Redis 3.0+ required
     *
     * @param slaves slaves amount for synchronization.
     *                 Default value is <code>0</code> which means available slaves
     *                 at the moment of execution and <code>-1</code> means no sync at all.
     * @param timeout synchronization timeout
     * @param unit synchronization timeout time unit
     * @return self instance
     */
    public TransactionOptions syncSlaves(int slaves, long timeout, TimeUnit unit) {
        this.syncSlaves = slaves;
        this.syncTimeout = unit.toMillis(timeout);
        return this;
    }

    public int getSyncSlaves() {
        return syncSlaves;
    }

    public long getTimeout() {
        return timeout;
    }
    /**
     * If transaction hasn't been committed within <code>timeout</code> it will rollback automatically.
     * Set <code>-1</code> to disable.
     * <p>
     * Default is <code>5000 milliseconds</code>
     * 
     * @param timeout in milliseconds
     * @param timeoutUnit timeout time unit
     * @return self instance
     */
    public TransactionOptions timeout(long timeout, TimeUnit timeoutUnit) {
        if (timeout == -1) {
            this.timeout = timeout;
            return this;
        }
        this.timeout = timeoutUnit.toMillis(timeout);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TransactionOptions that = (TransactionOptions) o;
        return responseTimeout == that.responseTimeout
                    && retryAttempts == that.retryAttempts
                        && retryInterval == that.retryInterval
                            && syncSlaves == that.syncSlaves
                                && syncTimeout == that.syncTimeout
                                    && timeout == that.timeout;
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseTimeout, retryAttempts, retryInterval, syncSlaves, syncTimeout, timeout);
    }
}
