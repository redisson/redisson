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

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.RObjectReactive;

import rx.Single;
import rx.SingleSubscriber;
import rx.subjects.PublishSubject;

/**
 * Base Redisson object
 *
 * @author Nikita Koksharov
 *
 */
abstract class RedissonObjectReactive implements RObjectReactive {

    final CommandReactiveExecutor commandExecutor;
    private final String name;
    final Codec codec;

    public RedissonObjectReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this.codec = codec;
        this.name = name;
        this.commandExecutor = commandExecutor;
    }

    public RedissonObjectReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    protected <V> SingleSubscriber<V> toSubscriber(final PublishSubject<V> promise) {
        return new SingleSubscriber<V>() {
            @Override
            public void onSuccess(V value) {
                promise.onNext(value);
                promise.onCompleted();
            }

            @Override
            public void onError(Throwable error) {
                promise.onError(error);
            }
        };
    }

    protected <V> PublishSubject<V> newObservable() {
        return PublishSubject.create();
    }

    protected <V> Single<V> newSucceededObservable(V result) {
        return Single.just(result);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Single<Void> rename(String newName) {
        return commandExecutor.writeObservable(getName(), RedisCommands.RENAME, getName(), newName);
    }

    @Override
    public Single<Void> migrate(String host, int port, int database) {
        return commandExecutor.writeObservable(getName(), RedisCommands.MIGRATE, host, port, getName(), database);
    }

    @Override
    public Single<Boolean> move(int database) {
        return commandExecutor.writeObservable(getName(), RedisCommands.MOVE, getName(), database);
    }

    @Override
    public Single<Boolean> renamenx(String newName) {
        return commandExecutor.writeObservable(getName(), RedisCommands.RENAMENX, getName(), newName);
    }

    @Override
    public Single<Boolean> delete() {
        return commandExecutor.writeObservable(getName(), RedisCommands.DEL_SINGLE, getName());
    }

}
