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
package org.redisson.api.redisnode;

/**
 * Redis Master node API interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RedisMaster extends RedisNode, RedisMasterAsync {

    /**
     * Warms up the connection pool for this Redis node until it contains the specified number of connections.
     * Connections currently in use are included in this number. If the pool already contains at least the specified
     * number, then no new connections are created.
     *
     * @param connectionAmount target connection amount
     */
    void warmUpConnectionPool(int connectionAmount);

}
