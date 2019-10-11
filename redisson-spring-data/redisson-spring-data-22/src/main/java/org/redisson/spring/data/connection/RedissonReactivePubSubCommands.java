/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.nio.ByteBuffer;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactivePubSubCommands;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.connection.ReactiveSubscription.ChannelMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactivePubSubCommands extends RedissonBaseReactive implements ReactivePubSubCommands {

    RedissonReactivePubSubCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Mono<ReactiveSubscription> createSubscription() {
        return Mono.just(new RedissonReactiveSubscription(executorService.getConnectionManager()));
    }

    @Override
    public Flux<Long> publish(Publisher<ChannelMessage<ByteBuffer, ByteBuffer>> messageStream) {
        return execute(messageStream, msg -> {
            return write(toByteArray(msg.getChannel()), StringCodec.INSTANCE, RedisCommands.PUBLISH, toByteArray(msg.getChannel()), toByteArray(msg.getMessage()));
        });
    }

    @Override
    public Mono<Void> subscribe(ByteBuffer... channels) {
        throw new UnsupportedOperationException("Subscribe through ReactiveSubscription object created by createSubscription method");
    }

    @Override
    public Mono<Void> pSubscribe(ByteBuffer... patterns) {
        throw new UnsupportedOperationException("Subscribe through ReactiveSubscription object created by createSubscription method");
    }

}
