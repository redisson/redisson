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

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.client.protocol.Time;

/**
 * Redis node interface
 *
 * @author Nikita Koksharov
 *
 */
@Deprecated
public interface Node extends NodeAsync {

    enum InfoSection {ALL, DEFAULT, SERVER, CLIENTS, MEMORY, PERSISTENCE, STATS, REPLICATION, CPU, COMMANDSTATS, CLUSTER, KEYSPACE}
    
    Map<String, String> info(InfoSection section);
    
    /**
     * Returns current Redis server time in seconds
     * 
     * @return time in seconds
     */
    Time time();
    
    /**
     * Returns node type
     *
     * @return node type
     */
    NodeType getType();

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
    
}
