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
package org.redisson.connection;

import org.redisson.api.NodeType;

import java.net.InetSocketAddress;

/**
 * Redis connection listener
 *
 * @author Nikita Koksharov
 *
 */
public interface ConnectionListener {

    /*
     * Implement onConnect(InetSocketAddress, NodeType) method instead.
     * It can be empty.
     */
    @Deprecated
    void onConnect(InetSocketAddress addr);

    /**
     * This method is triggered when Redisson
     * connected to Redis server.
     *
     * @param addr Redis server network address
     * @param nodeType type of Redis server
     */
    default void onConnect(InetSocketAddress addr, NodeType nodeType) {
        onConnect(addr);
    }

    /*
     * Implement onDisconnect(InetSocketAddress, NodeType) method instead.
     * It can be empty.
     */
    @Deprecated
    void onDisconnect(InetSocketAddress addr);

    /**
     * This method is triggered when Redisson
     * discovers that Redis server in disconnected state.
     *
     * @param addr Redis server network address
     * @param nodeType type of Redis server
     */
    default void onDisconnect(InetSocketAddress addr, NodeType nodeType) {
        onDisconnect(addr);
    }

}
