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

import java.util.Collection;

import org.redisson.connection.ConnectionListener;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface NodesGroup<N extends Node> {

    /**
     * Adds connection listener which will be triggered
     * when Redisson has just been connected to or disconnected from redis server
     *
     * @param connectionListener
     */
    int addConnectionListener(ConnectionListener connectionListener);

    /**
     * Removes connection listener by id
     *
     * @param listenerId
     */
    void removeConnectionListener(int listenerId);

    /**
     * Get all nodes by type
     *
     * @see {@link NodeType}
     *
     * @param type
     * @return
     */
    Collection<N> getNodes(NodeType type);

    /**
     * All Redis nodes used by Redisson.
     * This collection may change during master change, cluster topology update and etc.
     *
     * @return
     */
    Collection<N> getNodes();

    /**
     * Ping all Redis nodes
     *
     * @return <code>true</code> if all nodes have replied "PONG", <code>false</code> in other case.
     */
    boolean pingAll();

}
