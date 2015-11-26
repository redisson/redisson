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

import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import rx.Single;
import rx.SingleSubscriber;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandReactiveService extends CommandAsyncService implements CommandReactiveExecutor {

    static class ToObservableFuture<T> implements Single.OnSubscribe<T> {
        private final Future<? extends T> that;

        public ToObservableFuture(Future<? extends T> that) {
            this.that = that;
        }

        @Override
        public void call(final SingleSubscriber<? super T> subscriber) {
            that.addListener(new FutureListener<T>() {
                @Override
                public void operationComplete(Future<T> future) throws Exception {
                    if (!future.isSuccess()) {
                        subscriber.onError(future.cause());
                        return;
                    }

                    subscriber.onSuccess(future.getNow());
                }
            });
        }
    }

    public CommandReactiveService(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    @Override
    public <T, R> Single<R> writeObservable(String key, RedisCommand<T> command, Object ... params) {
        return writeObservable(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Single<R> writeObservable(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = writeAsync(key, codec, command, params);
        return Single.create(new ToObservableFuture<R>(f));
    }

    @Override
    public <T, R> Single<R> readObservable(String key, RedisCommand<T> command, Object ... params) {
        return readObservable(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> Single<R> readObservable(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> f = readAsync(key, codec, command, params);
        return Single.create(new ToObservableFuture<R>(f));
    }

    @Override
    public <T, R> Single<R> evalReadObservable(String key, Codec codec, RedisCommand<T> evalCommandType,
            String script, List<Object> keys, Object... params) {
        Future<R> f = evalReadAsync(key, codec, evalCommandType, script, keys, params);
        return Single.create(new ToObservableFuture<R>(f));
    }

    @Override
    public <T, R> Single<R> evalWriteObservable(String key, Codec codec, RedisCommand<T> evalCommandType,
            String script, List<Object> keys, Object... params) {
        Future<R> f = evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        return Single.create(new ToObservableFuture<R>(f));
    }

}
