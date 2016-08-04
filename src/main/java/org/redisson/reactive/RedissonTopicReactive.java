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
package org.redisson.reactive;

import java.util.Collections;
import java.util.List;

import org.reactivestreams.Publisher;
import org.redisson.PubSubMessageListener;
import org.redisson.PubSubStatusListener;
import org.redisson.api.RTopicReactive;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.MessageListener;
import org.redisson.core.StatusListener;
import org.redisson.pubsub.AsyncSemaphore;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> message
 */
public class RedissonTopicReactive<M> implements RTopicReactive<M> {

    private final CommandReactiveExecutor commandExecutor;
    private final String name;
    private final Codec codec;

    public RedissonTopicReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    public RedissonTopicReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.codec = codec;
    }

    @Override
    public List<String> getChannelNames() {
        return Collections.singletonList(name);
    }

    @Override
    public Publisher<Long> publish(M message) {
        return commandExecutor.writeReactive(name, codec, RedisCommands.PUBLISH, name, message);
    }

    @Override
    public Publisher<Integer> addListener(StatusListener listener) {
        return addListener(new PubSubStatusListener<Object>(listener, name));
    };

    @Override
    public Publisher<Integer> addListener(MessageListener<M> listener) {
        PubSubMessageListener<M> pubSubListener = new PubSubMessageListener<M>(listener, name);
        return addListener(pubSubListener);
    }

    private Publisher<Integer> addListener(final RedisPubSubListener<?> pubSubListener) {
        final Promise<Integer> promise = commandExecutor.getConnectionManager().newPromise();
        Future<PubSubConnectionEntry> future = commandExecutor.getConnectionManager().subscribe(codec, name, pubSubListener);
        future.addListener(new FutureListener<PubSubConnectionEntry>() {
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }

                promise.setSuccess(System.identityHashCode(pubSubListener));
            }
        });
        return new NettyFuturePublisher<Integer>(promise);
    }


    @Override
    public void removeListener(int listenerId) {
        AsyncSemaphore semaphore = commandExecutor.getConnectionManager().getSemaphore(name);
        semaphore.acquireUninterruptibly();
        
        PubSubConnectionEntry entry = commandExecutor.getConnectionManager().getPubSubEntry(name);
        if (entry == null) {
            semaphore.release();
            return;
        }

        entry.removeListener(name, listenerId);
        if (!entry.hasListeners(name)) {
            commandExecutor.getConnectionManager().unsubscribe(name, semaphore);
        } else {
            semaphore.release();
        }
    }


}
