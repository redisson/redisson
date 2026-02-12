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

import org.redisson.api.RExecutorBatchFuture;
import org.redisson.api.RExecutorFuture;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorBatchFuture extends CompletableFutureWrapper<Void> implements RExecutorBatchFuture {

    private final List<RExecutorFuture<?>> futures;

    public RedissonExecutorBatchFuture(CompletableFuture<Void> future, List<RExecutorFuture<?>> futures) {
        super(future);
        this.futures = futures;
    }
    
    @Override
    public List<RExecutorFuture<?>> getTaskFutures() {
        return futures;
    }
    
}
