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
package org.redisson.connection;

import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.RFuture;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CountListener implements FutureListener<Void> {
    
    private final RPromise<Void> res;
    
    private final AtomicInteger counter;
    
    public static RPromise<Void> create(RFuture<Void>... futures) {
        RPromise<Void> result = new RedissonPromise<Void>();
        FutureListener<Void> listener = new CountListener(result, futures.length);
        for (RFuture<Void> future : futures) {
            future.addListener(listener);
        }
        return result;
    }
    
    public CountListener(RPromise<Void> res, int amount) {
        super();
        this.res = res;
        this.counter = new AtomicInteger(amount);
    }

    @Override
    public void operationComplete(Future<Void> future) throws Exception {
        if (!future.isSuccess()) {
            res.tryFailure(future.cause());
            return;
        }
        if (counter.decrementAndGet() == 0) {
            res.trySuccess(null);
        }
    }

}
