/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.time.Instant;
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
     * Returns Redis memory statistics
     *
     * @return statistics info map
     */
    RFuture<Map<String, String>> getMemoryStatisticsAsync();

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

    /**
     * Returns information about Redis node.
     *
     * @param section - section of information
     * @return information map
     */
    RFuture<Map<String, String>> infoAsync(RedisNode.InfoSection section);

    /**
     * Get value of Redis configuration parameter.
     *
     * @param parameter - name of parameter
     * @return value of parameter
     */
    RFuture<Map<String, String>> getConfigAsync(String parameter);

    /**
     * Set value of Redis configuration parameter.
     *
     * @param parameter - name of parameter
     * @param value - value of parameter
     * @return void
     */
    RFuture<Void> setConfigAsync(String parameter, String value);

    /**
     * Runs the Redis database saving process in background.
     *
     */
    RFuture<Void> bgSaveAsync();

    /**
     * Save the Redis database in background.
     * If AOF rewrite process is in progress then
     * the background save is scheduled to run upon its completion.
     *
     */
    RFuture<Void> scheduleBgSaveAsync();

    /**
     * Save the Redis database.
     *
     */
    RFuture<Void> saveAsync();

    /**
     * Returns time of the last successful
     * Redis database save operation.
     *
     * @return time
     */
    RFuture<Instant> getLastSaveTimeAsync();

    /**
     * Runs an Append Only File rewrite process.
     * Starts only if there is no a background process doing persistence.
     * <p>
     * If fails no data gets lost
     *
     */
    RFuture<Void> bgRewriteAOFAsync();

    /**
     * Returns keys amount stored in this Redis node.
     *
     * @return keys amount
     */
    RFuture<Long> sizeAsync();

}
