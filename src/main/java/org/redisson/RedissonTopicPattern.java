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

import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.MessageListener;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> message
 */
public class RedissonTopicPattern<M> extends RedissonTopic<M> {

    protected RedissonTopicPattern(CommandExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public int addListener(MessageListener<M> listener) {
        PubSubMessageListenerWrapper<M> pubSubListener = new PubSubMessageListenerWrapper<M>(listener, getName());
        return addListener(pubSubListener);
    }

    private int addListener(PubSubMessageListenerWrapper<M> pubSubListener) {
        PubSubConnectionEntry entry = commandExecutor.getConnectionManager().psubscribe(getName());
        synchronized (entry) {
            if (entry.isActive()) {
                entry.addListener(getName(), pubSubListener);
                return pubSubListener.hashCode();
            }
        }
        // entry is inactive trying add again
        return addListener(pubSubListener);
    }

    @Override
    public void removeListener(int listenerId) {
        PubSubConnectionEntry entry = commandExecutor.getConnectionManager().getEntry(getName());
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.isActive()) {
                entry.removeListener(getName(), listenerId);
                if (entry.getListeners(getName()).isEmpty()) {
                    commandExecutor.getConnectionManager().punsubscribe(getName());
                }
                return;
            }
        }

        // entry is inactive trying add again
        removeListener(listenerId);
    }

}
