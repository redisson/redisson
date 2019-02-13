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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.RExecutorBatchFuture;
import org.redisson.api.RExecutorFuture;
import org.redisson.misc.RedissonPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorBatchFuture extends RedissonPromise<Void> implements RExecutorBatchFuture {

    private List<RExecutorFuture<?>> futures;

    public RedissonExecutorBatchFuture(List<RExecutorFuture<?>> futures) {
        this.futures = futures;
        
        final AtomicInteger counter = new AtomicInteger(futures.size());
        for (RExecutorFuture<?> future : futures) {
            future.onComplete((res, e) -> {
                if (e != null) {
                    RedissonExecutorBatchFuture.this.tryFailure(e);
                    return;
                }
                
                if (counter.decrementAndGet() == 0) {
                    RedissonExecutorBatchFuture.this.trySuccess(null);
                }
            });
        }
    }
    
    @Override
    public List<RExecutorFuture<?>> getTaskFutures() {
        return futures;
    }
    
}
