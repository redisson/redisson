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

import java.util.Collections;
import java.util.List;

import org.redisson.client.RedisPubSubListener;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.PatternMessageListener;
import org.redisson.core.PatternStatusListener;
import org.redisson.core.RPatternTopic;

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

    protected RedissonPatternTopic(CommandExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
    }

    @Override
    public int addListener(PatternStatusListener listener) {
        return addListener(new PubSubPatternStatusListener(listener, name));
    };

    @Override
    public int addListener(PatternMessageListener<M> listener) {
        PubSubPatternMessageListener<M> pubSubListener = new PubSubPatternMessageListener<M>(listener, name);
        return addListener(pubSubListener);
    }

    private int addListener(RedisPubSubListener<M> pubSubListener) {
        PubSubConnectionEntry entry = commandExecutor.getConnectionManager().psubscribe(name);
        synchronized (entry) {
            if (entry.isActive()) {
                entry.addListener(name, pubSubListener);
                return pubSubListener.hashCode();
            }
        }
        // entry is inactive trying add again
        return addListener(pubSubListener);
    }

    @Override
    public void removeListener(int listenerId) {
        PubSubConnectionEntry entry = commandExecutor.getConnectionManager().getEntry(name);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.isActive()) {
                entry.removeListener(name, listenerId);
                if (entry.getListeners(name).isEmpty()) {
                    commandExecutor.getConnectionManager().punsubscribe(name);
                }
                return;
            }
        }

        // entry is inactive trying add again
        removeListener(listenerId);
    }

    @Override
    public List<String> getPatternNames() {
        return Collections.singletonList(name);
    }

}
