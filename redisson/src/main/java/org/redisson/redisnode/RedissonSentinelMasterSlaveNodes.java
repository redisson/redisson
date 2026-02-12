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

import org.redisson.api.redisnode.RedisSentinel;
import org.redisson.api.redisnode.RedisSentinelMasterSlave;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.SentinelConnectionManager;
import org.redisson.misc.RedisURI;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSentinelMasterSlaveNodes extends RedissonMasterSlaveNodes implements RedisSentinelMasterSlave {

    public RedissonSentinelMasterSlaveNodes(ConnectionManager connectionManager, CommandAsyncExecutor commandExecutor) {
        super(connectionManager, commandExecutor);
    }

    @Override
    public Collection<RedisSentinel> getSentinels() {
        return ((SentinelConnectionManager) connectionManager).getSentinels().stream()
                .map(c -> new SentinelRedisNode(c, commandExecutor))
                    .collect(Collectors.toList());
    }

    @Override
    public RedisSentinel getSentinel(String address) {
        RedisURI addr = new RedisURI(address);
        return ((SentinelConnectionManager) connectionManager).getSentinels().stream()
                .filter(c -> addr.equals(c.getAddr()))
                .map(c -> new SentinelRedisNode(c, commandExecutor))
                .findFirst().orElse(null);
    }
}
