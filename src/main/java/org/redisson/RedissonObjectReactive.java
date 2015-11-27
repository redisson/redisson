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

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.RObjectReactive;

import reactor.core.reactivestreams.SubscriberBarrier;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.broadcast.Broadcaster;

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

    protected <V> Subscriber<V> toSubscriber(final Subscriber<V> promise) {
        return new SubscriberBarrier<V, V>(promise) {
            @Override
            protected void doSubscribe(Subscription subscription) {
                subscription.request(1);
            }
        };
    }

    protected <V> Processor<V, V> newObservable() {
        return Broadcaster.create();
    }

    protected <V> Stream<V> newSucceededObservable(V result) {
        return Streams.just(result);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Publisher<Void> rename(String newName) {
        return commandExecutor.writeObservable(getName(), RedisCommands.RENAME, getName(), newName);
    }

    @Override
    public Publisher<Void> migrate(String host, int port, int database) {
        return commandExecutor.writeObservable(getName(), RedisCommands.MIGRATE, host, port, getName(), database);
    }

    @Override
    public Publisher<Boolean> move(int database) {
        return commandExecutor.writeObservable(getName(), RedisCommands.MOVE, getName(), database);
    }

    @Override
    public Publisher<Boolean> renamenx(String newName) {
        return commandExecutor.writeObservable(getName(), RedisCommands.RENAMENX, getName(), newName);
    }

    @Override
    public Publisher<Boolean> delete() {
        return commandExecutor.writeObservable(getName(), RedisCommands.DEL_SINGLE, getName());
    }

}
