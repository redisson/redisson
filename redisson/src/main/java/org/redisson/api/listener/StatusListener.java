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
package org.redisson.api.listener;

import java.util.EventListener;

/**
 * Listener for Redis PubSub channel status changes
 *
 * @author Nikita Koksharov
 *
 * @see org.redisson.api.RTopic
 */
public interface StatusListener extends EventListener {

    /**
     * Executes then Redisson successfully subscribed to channel.
     * Invoked during re-connection or failover process
     * 
     * @param channel to subscribe
     */
    void onSubscribe(String channel);

    /**
     * Executes then Redisson successfully unsubscribed from channel.
     * 
     * @param channel to unsubscribe
     */
    void onUnsubscribe(String channel);

}
