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
package org.redisson.core;

import java.util.List;

/**
 * Distributed topic. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> the type of message object
 */
public interface RPatternTopic<M> {

    /**
     * Get topic channel patterns
     *
     * @return
     */
    List<String> getPatternNames();

    /**
     * Subscribes to this topic.
     * <code>MessageListener.onMessage</code> is called when any message
     * is published on this topic.
     *
     * @param listener
     * @return locally unique listener id
     * @see org.redisson.core.MessageListener
     */
    int addListener(PatternMessageListener<M> listener);

    /**
     * Subscribes to status changes of this topic
     *
     * @param listener
     * @return
     * @see org.redisson.core.StatusListener
     */
    int addListener(PatternStatusListener listener);

    /**
     * Removes the listener by <code>id</code> for listening this topic
     *
     * @param listenerId
     */
    void removeListener(int listenerId);

}
