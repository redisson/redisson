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

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class RedisNodes<T extends BaseRedisNodes> {

    public static final RedisNodes<RedisCluster> CLUSTER = new RedisNodes<>(RedisCluster.class);
    public static final RedisNodes<RedisMasterSlave> MASTER_SLAVE = new RedisNodes<>(RedisMasterSlave.class);
    public static final RedisNodes<RedisSentinelMasterSlave> SENTINEL_MASTER_SLAVE = new RedisNodes<>(RedisSentinelMasterSlave.class);
    public static final RedisNodes<RedisSingle> SINGLE = new RedisNodes<>(RedisSingle.class);

    private final Class<T> clazz;

    RedisNodes(Class<T> clazz) {
        this.clazz = clazz;
    }

    public Class<T> getClazz() {
        return clazz;
    }
}
