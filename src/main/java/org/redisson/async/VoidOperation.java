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
import io.netty.util.concurrent.Promise;

import com.lambdaworks.redis.RedisAsyncConnection;

public abstract class VoidOperation<V, R> implements AsyncOperation<V, Void> {

    @Override
    public void execute(Promise<Void> promise, RedisAsyncConnection<Object, V> async) {
        Future<R> future = execute(async);
        future.addListener(new VoidListener<V, R>(promise, async, this));
    }

    protected abstract Future<R> execute(RedisAsyncConnection<Object, V> async);

}
