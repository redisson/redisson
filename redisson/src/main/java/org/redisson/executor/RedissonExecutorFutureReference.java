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
package org.redisson.executor;

import org.redisson.api.RExecutorFuture;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorFutureReference extends SoftReference<RExecutorFuture<?>> {

    private final CompletableFuture<?> promise;
    private final String requestId;
    
    public RedissonExecutorFutureReference(String requestId, RExecutorFuture<?> referent, ReferenceQueue<? super RExecutorFuture<?>> q, CompletableFuture<?> promise) {
        super(referent, q);
        this.requestId = requestId;
        this.promise = promise;
    }
    
    public CompletableFuture<?> getPromise() {
        return promise;
    }
    
    public String getRequestId() {
        return requestId;
    }

}
