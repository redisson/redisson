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
 * Redis Sentinel Master Slave nodes API interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RedisSentinelMasterSlave extends RedisMasterSlave {

    /**
     * Returns collection of Redis Sentinel nodes belongs to this Redis setup.
     *
     * @return Redis Sentinel nodes
     */
    Collection<RedisSentinel> getSentinels();

    /**
     * Returns Redis Sentinel node by defined address.
     * <p>
     * Address example: <code>redis://127.0.0.1:9233</code>
     *
     * @return Redis Sentinel node
     */
    RedisSentinel getSentinel(String address);

}
