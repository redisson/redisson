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
package org.redisson.connection.balancer;

import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Load Balancer redirects specified commands to the Redis node with specified address.
 *
 * @author Nikita Koksharov
 *
 */
public class CommandsLoadBalancer extends RoundRobinLoadBalancer implements LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(CommandsLoadBalancer.class);

    private final Map<Pattern, Set<String>> commandsMap = new HashMap<>();

    private Set<String> commands;
    private RedisURI address;

    @Override
    public ClientConnectionsEntry getEntry(List<ClientConnectionsEntry> clientsCopy, RedisCommand<?> redisCommand) {
        String name = redisCommand.getName().toLowerCase(Locale.ENGLISH);

        if (commands != null
                && commands.contains(name)) {
            return clientsCopy.stream()
                                .filter(c -> address.equals(c.getClient().getAddr()))
                                .findAny()
                                .orElseGet(() -> {
                return getEntry(clientsCopy);
            });
        }

        for (Map.Entry<Pattern, Set<String>> e : commandsMap.entrySet()) {
            if (e.getValue().contains(name)) {
                List<ClientConnectionsEntry> s = filter(clientsCopy, e.getKey());
                if (!s.isEmpty()) {
                    return getEntry(s);
                }
            }
        }

        return getEntry(clientsCopy);
    }

    /**
     * Defines Redis node address where the commands are redirected to
     *
     * @param address Redis node address
     */
    @Deprecated
    public void setAddress(String address) {
        log.warn("address setting is deprecated. Use commandsMap setting instead.");
        this.address = new RedisURI(address);
    }

    /**
     * Defines command names which are redirected to the Redis node
     * specified by {@link #setAddress(String)}
     *
     * @param commands commands list
     */
    @Deprecated
    public void setCommands(List<String> commands) {
        log.warn("commands setting is deprecated. Use commandsMap setting instead.");
        this.commands = commands.stream()
                                    .map(c -> c.toLowerCase(Locale.ENGLISH))
                                    .collect(Collectors.toSet());
    }

    /**
     * Defines command names mapped per host name regular expression.
     * <p>
     * YAML definition example:
     * <pre>
     *      loadBalancer: !&lt;org.redisson.connection.balancer.CommandsLoadBalancer&gt;
     *       commandsMap:
     *           "slavehost1.*" : ["get", "hget"]
     *           "slavehost2.*" : ["mget", "publish"]
     * </pre>
     *
     * @param value a map where the key is a host name regular expression,
     *                 and the value is an array of command names
     *                 that should be executed.
     */
    public void setCommandsMap(Map<String, Set<String>> value) {
        for (Map.Entry<String, Set<String>> e : value.entrySet()) {
            Set<String> cc = e.getValue().stream()
                                            .map(c -> c.toLowerCase(Locale.ENGLISH))
                                            .collect(Collectors.toSet());
            this.commandsMap.put(Pattern.compile(e.getKey()), cc);
        }
    }

}
