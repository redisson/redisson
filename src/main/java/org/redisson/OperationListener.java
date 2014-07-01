/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson;

import com.lambdaworks.redis.RedisAsyncConnection;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public abstract class OperationListener<V, F> implements FutureListener<F> {

    private Promise<V> promise;
    private RedisAsyncConnection<Object, V> async;

    public OperationListener(Promise<V> promise, RedisAsyncConnection<Object, V> async) {
        super();
        this.promise = promise;
        this.async = async;
    }

    @Override
    public void operationComplete(Future<F> future) throws Exception {
        if (isBreak(async, promise, future)) {
            return;
        }
        
        onOperationComplete(future);
    }
    
    public abstract void onOperationComplete(Future<F> future) throws Exception;

    protected boolean isBreak(RedisAsyncConnection<Object, V> async, Promise<V> promise, Future<F> future) {
        if (!future.isSuccess()) {
            promise.setFailure(future.cause());
            return true;
        }
        if (promise.isCancelled()) {
            if (async.isMultiMode()) {
                async.discard();
            }
            return true;
        }
        
        return false;
    }

}
