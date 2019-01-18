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
package org.redisson.executor;

import org.redisson.api.RExecutorFuture;
import org.redisson.misc.PromiseDelegator;
import org.redisson.misc.RPromise;
import org.redisson.remote.RequestId;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonExecutorFuture<V> extends PromiseDelegator<V> implements RExecutorFuture<V> {

    private final RequestId taskId;
    
    public RedissonExecutorFuture(RemotePromise<V> promise) {
        this(promise, promise.getRequestId());
    }
    
    public RedissonExecutorFuture(RPromise<V> promise, RequestId taskId) {
        super(promise);
        this.taskId = taskId;
    }

    @Override
    public String getTaskId() {
        return taskId.toString();
    }

}
