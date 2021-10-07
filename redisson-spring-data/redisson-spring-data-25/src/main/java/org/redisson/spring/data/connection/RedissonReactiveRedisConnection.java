/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.*;

import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveRedisConnection extends RedissonBaseReactive implements ReactiveRedisConnection {

    public RedissonReactiveRedisConnection(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Mono<Void> closeLater() {
        return Mono.empty();
    }

    @Override
    public ReactiveKeyCommands keyCommands() {
        return new RedissonReactiveKeyCommands(executorService);
    }

    @Override
    public ReactiveStringCommands stringCommands() {
        return new RedissonReactiveStringCommands(executorService);
    }

    @Override
    public ReactiveNumberCommands numberCommands() {
        return new RedissonReactiveNumberCommands(executorService);
    }

    @Override
    public ReactiveListCommands listCommands() {
        return new RedissonReactiveListCommands(executorService);
    }

    @Override
    public ReactiveSetCommands setCommands() {
        return new RedissonReactiveSetCommands(executorService);
    }

    @Override
    public ReactiveZSetCommands zSetCommands() {
        return new RedissonReactiveZSetCommands(executorService);
    }

    @Override
    public ReactiveHashCommands hashCommands() {
        return new RedissonReactiveHashCommands(executorService);
    }

    @Override
    public ReactiveGeoCommands geoCommands() {
        return new RedissonReactiveGeoCommands(executorService);
    }

    @Override
    public ReactiveHyperLogLogCommands hyperLogLogCommands() {
        return new RedissonReactiveHyperLogLogCommands(executorService);
    }

    @Override
    public ReactivePubSubCommands pubSubCommands() {
        return new RedissonReactivePubSubCommands(executorService);
    }

    @Override
    public ReactiveScriptingCommands scriptingCommands() {
        return new RedissonReactiveScriptingCommands(executorService);
    }

    @Override
    public ReactiveServerCommands serverCommands() {
        return new RedissonReactiveServerCommands(executorService);
    }

    @Override
    public ReactiveStreamCommands streamCommands() {
        return new RedissonReactiveStreamCommands(executorService);
    }

    @Override
    public Mono<String> ping() {
        return read(null, StringCodec.INSTANCE, RedisCommands.PING);
    }

}
