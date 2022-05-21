/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.api;

import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.StatusListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive interface for Sharded Topic. Messages are delivered to message listeners connected to the same Topic.
 *
 * @author Nikita Koksharov
 *
 */
public interface RShardedTopicReactive {

    /**
     * Get topic channel names
     *
     * @return channel names
     */
    List<String> getChannelNames();

    /**
     * Publish the message to all subscribers of this topic asynchronously
     *
     * @param message to send
     * @return the <code>Future</code> object with number of clients that received the message
     */
    Mono<Long> publish(Object message);

    /**
     * Subscribes to status changes of this topic
     *
     * @param listener for messages
     * @return listener id
     * @see org.redisson.api.listener.StatusListener
     */
    Mono<Integer> addListener(StatusListener listener);

    /**
     * Subscribes to this topic.
     * <code>MessageListener.onMessage</code> is called when any message
     * is published on this topic.
     *
     * @param <M> type of message
     * @param type - type of message
     * @param listener for messages
     * @return locally unique listener id
     * @see org.redisson.api.listener.MessageListener
     */
    <M> Mono<Integer> addListener(Class<M> type, MessageListener<M> listener);

    /**
     * Removes the listener by <code>id</code> for listening this topic
     *
     * @param listenerIds - message listener ids
     * @return void
     */
    Mono<Void> removeListener(Integer... listenerIds);

    /**
     * Removes the listener by <code>instance</code> for listening this topic
     *
     * @param listener - message listener
     * @return void
     */
    Mono<Void> removeListener(MessageListener<?> listener);

    /**
     * Returns continues stream of published messages.
     *
     * @param <M> type of message
     * @param type - type of message to listen
     * @return stream of messages
     */
    <M> Flux<M> getMessages(Class<M> type);

    /**
     * Removes all listeners from this topic
     *
     * @return void
     */
    Mono<Void> removeAllListeners();

}
