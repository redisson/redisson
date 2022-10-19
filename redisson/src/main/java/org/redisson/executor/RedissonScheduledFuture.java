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
package org.redisson.executor;

import org.redisson.api.RScheduledFuture;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonScheduledFuture<V> extends CompletableFutureWrapper<V> implements RScheduledFuture<V> {

    private final long scheduledExecutionTime;
    private final String taskId;
    private final RemotePromise<V> promise;

    public RedissonScheduledFuture(RemotePromise<V> promise, long scheduledExecutionTime) {
        super(promise);
        this.scheduledExecutionTime = scheduledExecutionTime;
        this.taskId = promise.getRequestId();
        this.promise = promise;
    }

    public RemotePromise<V> getInnerPromise() {
        return promise;
    }
    
    @Override
    public int compareTo(Delayed other) {
        if (this == other) {
            return 0;
        }
        
        long diff = getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);

        if (diff == 0) {
            return 0;
        }
        if (diff < 0) {
            return -1;
        }
        return 1;
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(scheduledExecutionTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }
    
    @Override
    public String getTaskId() {
        return taskId;
    }

}
