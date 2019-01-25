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

import java.util.List;

import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.StatusListener;

/**
 * Distributed topic. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 */
public interface RTopic extends RTopicAsync {

    /**
     * Get topic channel names
     *
     * @return channel names
     */
    List<String> getChannelNames();

    /**
     * Publish the message to all subscribers of this topic
     *
     * @param message to send
     * @return the number of clients that received the message
     */
    long publish(Object message);

    /**
     * Subscribes to this topic.
     * <code>MessageListener.onMessage</code> is called when any message
     * is published on this topic.
     *
     * @param <M> - type of message
     * @param type - type of message
     * @param listener for messages
     * @return locally unique listener id
     * @see org.redisson.api.listener.MessageListener
     */
    <M> int addListener(Class<M> type, MessageListener<? extends M> listener);

    /**
     * Subscribes to status changes of this topic
     *
     * @param listener for messages
     * @return listener id
     * @see org.redisson.api.listener.StatusListener
     */
    int addListener(StatusListener listener);

    /**
     * Removes the listener by its instance
     *
     * @param listener - listener instance
     */
    void removeListener(MessageListener<?> listener);
    
    /**
     * Removes the listener by <code>id</code> for listening this topic
     *
     * @param listenerIds - listener ids
     */
    void removeListener(Integer... listenerIds);

    /**
     * Removes all listeners from this topic
     */
    void removeAllListeners();

    /**
     * Returns amount of registered listeners to this topic
     * 
     * @return amount of listeners
     */
    int countListeners();
    
    /**
     * Returns amount of subscribers to this topic across all Redisson instances.
     * Each subscriber may have multiple listeners.
     * 
     * @return amount of subscribers
     */
    long countSubscribers();
}
