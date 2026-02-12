/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.redisnode;

import org.redisson.api.NodeType;
import org.redisson.api.redisnode.RedisCluster;
import org.redisson.api.redisnode.RedisClusterMaster;
import org.redisson.api.redisnode.RedisClusterSlave;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.ConnectionManager;

import java.util.Collection;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonClusterNodes extends RedissonBaseNodes implements RedisCluster {

    public RedissonClusterNodes(ConnectionManager connectionManager, CommandAsyncExecutor commandExecutor) {
        super(connectionManager, commandExecutor);
    }

    @Override
    public Collection<RedisClusterMaster> getMasters() {
        return getNodes(NodeType.MASTER);
    }

    @Override
    public RedisClusterMaster getMaster(String address) {
        return getNode(address, NodeType.MASTER);
    }

    @Override
    public Collection<RedisClusterSlave> getSlaves() {
        return getNodes(NodeType.SLAVE);
    }

    @Override
    public RedisClusterSlave getSlave(String address) {
        return getNode(address, NodeType.SLAVE);
    }

}
