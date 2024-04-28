/**
 * Copyright (c) 2013-2024 Nikita Koksharov
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api;

import java.util.List;

import org.redisson.api.listener.PatternMessageListener;
import org.redisson.api.listener.PatternStatusListener;

/**
 * Pattern based observer for Publish Subscribe object.
 *
 * @author Nikita Koksharov
 *
 */
public interface RPatternTopic {

    /**
     * Get topic channel patterns
     *
     * @return list of topic names
     */
    List<String> getPatternNames();

    /**
     * Subscribes to this topic.
     * <code>MessageListener.onMessage</code> is called when any message
     * is published on this topic.
     *
     * @param <T> type of message
     * @param type - type of message
     * @param listener - message listener
     * @return local JVM unique listener id
     * @see org.redisson.api.listener.MessageListener
     */
    <T> int addListener(Class<T> type, PatternMessageListener<T> listener);

    /**
     * Subscribes to status changes of this topic
     *
     * @param listener - message listener
     * @return local JVM unique listener id
     * @see org.redisson.api.listener.StatusListener
     */
    int addListener(PatternStatusListener listener);

    /**
     * Removes the listener by <code>id</code> for listening this topic
     *
     * @param listenerId - id of message listener
     */
    void removeListener(int listenerId);

    /**
     * Removes the listener by its instance
     *
     * @param listener - listener instance
     */
    void removeListener(PatternMessageListener<?> listener);

    /**
     * Removes all listeners from this topic
     */
    void removeAllListeners();

    /**
     * Removes all listeners from this topic
     *
     * @return void
     */
    RFuture<Void> removeAllListenersAsync();

    RFuture<Integer> addListenerAsync(PatternStatusListener listener);

    <T> RFuture<Integer> addListenerAsync(Class<T> type, PatternMessageListener<T> listener);

    RFuture<Void> removeListenerAsync(int listenerId);

    /**
     * Returns amount of registered listeners to this pattern topic
     *
     * @return amount of listeners
     */
    RFuture<Long> countSubscribersAsync();

    /**
     * Returns amount of subscribers to this pattern topic across all Redisson instances.
     * Each subscriber may have multiple listeners.
     *
     * @return amount of subscribers
     */
    long countSubscribers();


    /**
     * Returns amount of list channel to this pattern
     * An active channel is a Pub/Sub channel with one or more subscribers (excluding clients subscribed to patterns).
     * @return all actives channel to this pattern topic
     */
    RFuture<List<String>> getMatchTopicsAsync();


    /**
     * Returns amount of list channel to this pattern
     * An active channel is a Pub/Sub channel with one or more subscribers (excluding clients subscribed to patterns).
     * @return all actives channel to this pattern topic
     */
    List<String> getMatchTopics();

}
