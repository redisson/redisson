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
import org.redisson.PubSubPatternMessageListener;
import org.redisson.PubSubPatternStatusListener;
import org.redisson.api.RPatternTopicReactive;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.PatternMessageListener;
import org.redisson.core.PatternStatusListener;
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
public class RedissonPatternTopicReactive<M> implements RPatternTopicReactive<M> {

    final CommandReactiveExecutor commandExecutor;
    private final String name;
    private final Codec codec;

    public RedissonPatternTopicReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    public RedissonPatternTopicReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.codec = codec;
    }

    @Override
    public Publisher<Integer> addListener(PatternStatusListener listener) {
        Promise<Integer> promise = commandExecutor.getConnectionManager().newPromise();
        addListener(new PubSubPatternStatusListener(listener, name), promise);
        return new NettyFuturePublisher<Integer>(promise);
    };

    @Override
    public Publisher<Integer> addListener(PatternMessageListener<M> listener) {
        Promise<Integer> promise = commandExecutor.getConnectionManager().newPromise();
        PubSubPatternMessageListener<M> pubSubListener = new PubSubPatternMessageListener<M>(listener, name);
        addListener(pubSubListener, promise);
        return new NettyFuturePublisher<Integer>(promise);
    }

    private void addListener(final RedisPubSubListener<M> pubSubListener, final Promise<Integer> promise) {
        Future<PubSubConnectionEntry> future = commandExecutor.getConnectionManager().psubscribe(name, codec, pubSubListener);
        future.addListener(new FutureListener<PubSubConnectionEntry>() {
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }

                promise.setSuccess(pubSubListener.hashCode());
            }
        });
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
            commandExecutor.getConnectionManager().punsubscribe(name, semaphore);
        } else {
            semaphore.release();
        }
    }

    @Override
    public List<String> getPatternNames() {
        return Collections.singletonList(name);
    }

}
