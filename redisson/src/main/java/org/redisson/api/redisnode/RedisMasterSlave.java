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

import java.util.Collection;

/**
 * Redis Master Slave nodes API interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RedisMasterSlave extends BaseRedisNodes {

    /**
     * Returns Redis Master node belong to this Redis setup.
     *
     * @return Redis Master nodes
     */
    RedisMaster getMaster();

    /**
     * Returns Redis Master node by defined address.
     * <p>
     * Address example: <code>redis://127.0.0.1:9233</code>
     *
     * @return Redis Master node
     */
    RedisMaster getMaster(String address);

    /**
     * Returns collection of Redis Slave nodes belongs to this Redis setup.
     *
     * @return Redis Slave nodes
     */
    Collection<RedisSlave> getSlaves();

    /**
     * Returns Redis Slave node by defined address.
     * <p>
     * Address example: <code>redis://127.0.0.1:9233</code>
     *
     * @return Redis Slave node
     */
    RedisSlave getSlave(String address);

}
