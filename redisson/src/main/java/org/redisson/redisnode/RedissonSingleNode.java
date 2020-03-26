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
package org.redisson.redisnode;

import org.redisson.api.NodeType;
import org.redisson.api.redisnode.RedisMaster;
import org.redisson.api.redisnode.RedisSingle;
import org.redisson.connection.ConnectionManager;

import java.util.Collection;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSingleNode extends RedissonBaseNodes implements RedisSingle {

    public RedissonSingleNode(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    @Override
    public RedisMaster getInstance() {
        Collection<RedisMaster> list = getNodes(NodeType.MASTER);
        return list.iterator().next();
    }
}
