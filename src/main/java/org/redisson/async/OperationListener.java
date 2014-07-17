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
package org.redisson.async;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisTimeoutException;

public abstract class OperationListener<V, P, F> implements FutureListener<F> {

    final Promise<P> promise;
    final RedisAsyncConnection<Object, V> async;
    final AsyncOperation<V, P> timeoutCallback;

    public OperationListener(Promise<P> promise, RedisAsyncConnection<Object, V> async, AsyncOperation<V, P> timeoutCallback) {
        super();
        this.promise = promise;
        this.async = async;
        this.timeoutCallback = timeoutCallback;
    }

    @Override
    public void operationComplete(Future<F> future) throws Exception {
        if (isBreak(async, promise, future)) {
            return;
        }
        
        onOperationComplete(future);
    }
    
    public abstract void onOperationComplete(Future<F> future) throws Exception;

    protected boolean isBreak(RedisAsyncConnection<Object, V> async, Promise<P> promise, Future<F> future) {
        if (!future.isSuccess()) {
            if (future.cause() instanceof RedisTimeoutException) {
                timeoutCallback.execute(promise, async);
                return false;
            } else {
                promise.setFailure(future.cause());
                return true;
            }
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
