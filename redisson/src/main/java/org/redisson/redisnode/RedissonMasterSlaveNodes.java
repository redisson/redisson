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
package org.redisson.redisnode;

import org.redisson.api.NodeType;
import org.redisson.api.redisnode.RedisMaster;
import org.redisson.api.redisnode.RedisMasterSlave;
import org.redisson.api.redisnode.RedisSlave;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.ConnectionManager;

import java.util.Collection;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonMasterSlaveNodes extends RedissonBaseNodes implements RedisMasterSlave {

    public RedissonMasterSlaveNodes(ConnectionManager connectionManager, CommandAsyncExecutor commandExecutor) {
        super(connectionManager, commandExecutor);
    }

    @Override
    public RedisMaster getMaster() {
        Collection<RedisMaster> list = getNodes(NodeType.MASTER);
        if (list.isEmpty()) {
            return null;
        }
        return list.iterator().next();
    }

    @Override
    public RedisMaster getMaster(String address) {
        return getNode(address, NodeType.MASTER);
    }

    @Override
    public Collection<RedisSlave> getSlaves() {
        return getNodes(NodeType.SLAVE);
    }

    @Override
    public RedisSlave getSlave(String address) {
        return getNode(address, NodeType.SLAVE);
    }

}
