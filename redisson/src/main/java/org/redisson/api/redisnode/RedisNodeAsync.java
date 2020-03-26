/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
package org.redisson.api.redisnode;

import org.redisson.api.RFuture;
import org.redisson.client.protocol.Time;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base Redis node API interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RedisNodeAsync {

    /**
     * Returns current Redis server time in seconds
     *
     * @return time in seconds
     */
    RFuture<Time> timeAsync();

    /**
     * Ping Redis node.
     * Default timeout is 1000 milliseconds
     *
     * @return <code>true</code> if "PONG" reply received, <code>false</code> otherwise
     */
    RFuture<Boolean> pingAsync();

    /**
     * Ping Redis node with specified timeout.
     *
     * @param timeout - ping timeout
     * @param timeUnit - timeout unit
     * @return <code>true</code> if "PONG" reply received, <code>false</code> otherwise
     */
    RFuture<Boolean> pingAsync(long timeout, TimeUnit timeUnit);

    RFuture<Map<String, String>> infoAsync(RedisNode.InfoSection section);

}
