/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

/**
 *
 * Asynchronous interface for Reliable topic based on Redis Stream object.
 * <p>
 * Dedicated Redis connection is allocated per instance (subscriber) of this object.
 * Messages are delivered to all listeners attached to the same Redis setup.
 * <p>
 * Requires <b>Redis 5.0.0 and higher.</b>
 * <p>
 * @author Nikita Koksharov
 *
 */
public interface RReliableTopicAsync extends RExpirableAsync {

    /**
     * Amount of messages stored in Redis Stream object.
     *
     * @return amount of messages
     */
    RFuture<Long> sizeAsync();

    /**
     * Publish the message to all subscribers of this topic asynchronously.
     * Each subscriber may have multiple listeners.
     *
     * @param message to send
     * @return number of subscribers that received the message
     */
    RFuture<Long> publishAsync(Object message);
    
    /**
     * Subscribes to this topic.
     * <code>MessageListener.onMessage</code> method is called when any message
     * is published on this topic.
     * <p>
     * Though messages broadcasted across all topic instances, listener is attached to this topic instance.
     * <p>
     * Watchdog is started when listener was registered.
     *
     * @see org.redisson.config.Config#setReliableTopicWatchdogTimeout(long)
     *
     * @param <M> - type of message
     * @param type - type of message
     * @param listener for messages
     * @return id of listener attached to this topic instance
     * @see MessageListener
     */
    <M> RFuture<String> addListenerAsync(Class<M> type, MessageListener<M> listener);
    
    /**
     * Removes the listener by <code>id</code> attached to this topic instance
     *
     * @param listenerIds - listener ids
     * @return void
     */
    RFuture<Void> removeListenerAsync(String... listenerIds);

    /**
     * Removes all listeners attached to this topic instance
     */
    RFuture<Void> removeAllListenersAsync();

    /**
     * Returns amount of subscribers to this topic across all Redisson instances.
     * Each subscriber may have multiple listeners.
     *
     * @return amount of subscribers
     */
    RFuture<Integer> countSubscribersAsync();

}
