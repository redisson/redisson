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
package org.redisson.spring.data.connection;

import org.redisson.client.protocol.RedisCommands;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.*;

import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveRedisClusterConnection extends RedissonReactiveRedisConnection implements ReactiveRedisClusterConnection {

    public RedissonReactiveRedisClusterConnection(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public ReactiveClusterKeyCommands keyCommands() {
        return new RedissonReactiveClusterKeyCommands(executorService);
    }

    @Override
    public ReactiveClusterStringCommands stringCommands() {
        return new RedissonReactiveClusterStringCommands(executorService);
    }

    @Override
    public ReactiveClusterNumberCommands numberCommands() {
        return new RedissonReactiveClusterNumberCommands(executorService);
    }

    @Override
    public ReactiveClusterListCommands listCommands() {
        return new RedissonReactiveClusterListCommands(executorService);
    }

    @Override
    public ReactiveClusterSetCommands setCommands() {
        return new RedissonReactiveClusterSetCommands(executorService);
    }

    @Override
    public ReactiveClusterZSetCommands zSetCommands() {
        return new RedissonReactiveClusterZSetCommands(executorService);
    }

    @Override
    public ReactiveClusterHashCommands hashCommands() {
        return new RedissonReactiveClusterHashCommands(executorService);
    }

    @Override
    public ReactiveClusterGeoCommands geoCommands() {
        return new RedissonReactiveClusterGeoCommands(executorService);
    }

    @Override
    public ReactiveClusterHyperLogLogCommands hyperLogLogCommands() {
        return new RedissonReactiveClusterHyperLogLogCommands(executorService);
    }

    @Override
    public ReactiveClusterServerCommands serverCommands() {
        return new RedissonReactiveClusterServerCommands(executorService);
    }

    @Override
    public ReactiveClusterStreamCommands streamCommands() {
        return new RedissonReactiveClusterStreamCommands(executorService);
    }

    @Override
    public Mono<String> ping(RedisClusterNode node) {
        return execute(node, RedisCommands.PING);
    }

}
