/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson.connection.balancer;

import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.misc.RedisURI;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Load Balancer redirects specified commands to the Redis node with specified address.
 *
 * @author Nikita Koksharov
 *
 */
public class CommandsLoadBalancer extends RoundRobinLoadBalancer implements LoadBalancer {

    private Set<String> commands;
    private RedisURI address;

    @Override
    public ClientConnectionsEntry getEntry(List<ClientConnectionsEntry> clientsCopy, RedisCommand<?> redisCommand) {
        if (commands.contains(redisCommand.getName().toLowerCase())) {
            return clientsCopy.stream()
                                .filter(c -> address.equals(c.getClient().getAddr()))
                                .findAny()
                                .orElseGet(() -> {
                return getEntry(clientsCopy);
            });
        }
        return getEntry(clientsCopy);
    }

    /**
     * Defines Redis node address where the commands are redirected to
     *
     * @param address Redis node address
     */
    public void setAddress(String address) {
        this.address = new RedisURI(address);
    }

    /**
     * Defines command names which are redirected to the Redis node
     * specified by {@link #setAddress(String)}
     *
     * @param commands commands list
     */
    public void setCommands(List<String> commands) {
        this.commands = commands.stream()
                                    .map(c -> c.toLowerCase())
                                    .collect(Collectors.toSet());
    }
}
