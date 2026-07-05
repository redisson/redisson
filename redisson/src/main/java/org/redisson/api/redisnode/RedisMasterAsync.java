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

import org.redisson.api.RFuture;

/**
 * Redis Master node API interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RedisMasterAsync extends RedisNodeAsync {

    /**
     * Warms up connection pool for this Redis node until the specified amount of free connections is available.
     * If current free connections amount is greater than or equal to specified value, then no new connections are
     * created.
     *
     * @param connectionAmount - free connections amount
     * @return future completed once the requested free connections amount is reached
     */
    RFuture<Void> warmUpConnectionPoolAsync(int connectionAmount);

}
