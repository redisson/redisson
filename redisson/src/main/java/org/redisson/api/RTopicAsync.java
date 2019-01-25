/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

/**
 * Distributed topic. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 */
public interface RTopicAsync {

    /**
     * Publish the message to all subscribers of this topic asynchronously
     *
     * @param message to send
     * @return number of clients that received the message
     */
    RFuture<Long> publishAsync(Object message);
    
    /**
     * Subscribes to status changes of this topic
     *
     * @param listener for messages
     * @return listener id
     * @see org.redisson.api.listener.StatusListener
     */
    RFuture<Integer> addListenerAsync(StatusListener listener);
    
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
    <M> RFuture<Integer> addListenerAsync(Class<M> type, MessageListener<M> listener);
    
    /**
     * Removes the listener by <code>id</code> for listening this topic
     *
     * @param listenerIds - listener ids
     * @return void
     */
    RFuture<Void> removeListenerAsync(Integer... listenerIds);

    /**
     * Removes the listener by its instance
     *
     * @param listener - listener instance
     * @return void
     */
    RFuture<Void> removeListenerAsync(MessageListener<?> listener);
    
    /**
     * Returns amount of subscribers to this topic across all Redisson instances.
     * Each subscriber may have multiple listeners.
     * 
     * @return amount of subscribers
     */
    RFuture<Long> countSubscribersAsync();
    
}
