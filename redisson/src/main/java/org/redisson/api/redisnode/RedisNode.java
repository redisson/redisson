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
package org.redisson.api.redisnode;

import org.redisson.client.protocol.Time;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base Redis node API interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RedisNode {

    /**
     * Returns Redis memory statistics
     *
     * @return statistics info map
     */
    Map<String, String> getMemoryStatistics();

    /**
     * Returns current Redis server time in seconds
     *
     * @return time in seconds
     */
    Time time();

    /**
     * Get Redis node address
     *
     * @return node address
     */
    InetSocketAddress getAddr();

    /**
     * Ping Redis node.
     * Default timeout is 1000 milliseconds
     *
     * @return <code>true</code> if "PONG" reply received, <code>false</code> otherwise
     */
    boolean ping();

    /**
     * Ping Redis node with specified timeout.
     *
     * @param timeout - ping timeout
     * @param timeUnit - timeout unit
     * @return <code>true</code> if "PONG" reply received, <code>false</code> otherwise
     */
    boolean ping(long timeout, TimeUnit timeUnit);

    enum InfoSection {ALL, DEFAULT, SERVER, CLIENTS, MEMORY, PERSISTENCE, STATS, REPLICATION, CPU, COMMANDSTATS, CLUSTER, KEYSPACE}

    /**
     * Returns information about Redis node.
     *
     * @param section - section of information
     * @return information
     */
    Map<String, String> info(RedisNode.InfoSection section);

    /**
     * Get value of Redis configuration parameter.
     *
     * @param parameter - name of parameter
     * @return value of parameter
     */
    Map<String, String> getConfig(String parameter);

    /**
     * Set value of Redis configuration parameter.
     *
     * @param parameter - name of parameter
     * @param value - value of parameter
     */
    void setConfig(String parameter, String value);

}
