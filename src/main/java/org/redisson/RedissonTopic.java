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
package org.redisson;

import java.util.Collections;
import java.util.List;

import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;
import org.redisson.core.StatusListener;
import org.redisson.pubsub.AsyncSemaphore;

import io.netty.util.concurrent.Future;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> message
 */
public class RedissonTopic<M> implements RTopic<M> {

    final CommandAsyncExecutor commandExecutor;
    private final String name;
    private final Codec codec;

    protected RedissonTopic(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    protected RedissonTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.codec = codec;
    }

    public List<String> getChannelNames() {
        return Collections.singletonList(name);
    }

    @Override
    public long publish(M message) {
        return commandExecutor.get(publishAsync(message));
    }

    @Override
    public Future<Long> publishAsync(M message) {
        return commandExecutor.writeAsync(name, codec, RedisCommands.PUBLISH, name, message);
    }

    @Override
    public int addListener(StatusListener listener) {
        return addListener(new PubSubStatusListener<Object>(listener, name));
    };

    @Override
    public int addListener(MessageListener<M> listener) {
        PubSubMessageListener<M> pubSubListener = new PubSubMessageListener<M>(listener, name);
        return addListener(pubSubListener);
    }

    private int addListener(RedisPubSubListener<?> pubSubListener) {
        Future<PubSubConnectionEntry> future = commandExecutor.getConnectionManager().subscribe(codec, name, pubSubListener);
        future.syncUninterruptibly();
        return System.identityHashCode(pubSubListener);
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
