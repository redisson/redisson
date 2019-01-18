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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import org.redisson.api.RExecutorFuture;
import org.redisson.misc.RPromise;
import org.redisson.remote.RequestId;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorFutureReference extends WeakReference<RExecutorFuture<?>> {

    private final RPromise<?> promise;
    private final RequestId requestId;
    
    public RedissonExecutorFutureReference(RequestId requestId, RExecutorFuture<?> referent, ReferenceQueue<? super RExecutorFuture<?>> q, RPromise<?> promise) {
        super(referent, q);
        this.requestId = requestId;
        this.promise = promise;
    }
    
    public RPromise<?> getPromise() {
        return promise;
    }
    
    public RequestId getRequestId() {
        return requestId;
    }

}
