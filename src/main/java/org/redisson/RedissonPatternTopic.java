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
import org.redisson.command.CommandExecutor;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.PatternMessageListener;
import org.redisson.core.PatternStatusListener;
import org.redisson.core.RPatternTopic;
import org.redisson.pubsub.AsyncSemaphore;

import io.netty.util.concurrent.Future;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> message
 */
public class RedissonPatternTopic<M> implements RPatternTopic<M> {

    final CommandExecutor commandExecutor;
    private final String name;
    private final Codec codec;

    protected RedissonPatternTopic(CommandExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    protected RedissonPatternTopic(Codec codec, CommandExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.codec = codec;
    }

    @Override
    public int addListener(PatternStatusListener listener) {
        return addListener(new PubSubPatternStatusListener<Object>(listener, name));
    };

    @Override
    public int addListener(PatternMessageListener<M> listener) {
        PubSubPatternMessageListener<M> pubSubListener = new PubSubPatternMessageListener<M>(listener, name);
        return addListener(pubSubListener);
    }

    private int addListener(RedisPubSubListener<?> pubSubListener) {
        Future<PubSubConnectionEntry> future = commandExecutor.getConnectionManager().psubscribe(name, codec, pubSubListener);
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
