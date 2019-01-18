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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisClusterNode;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class RedissonBaseReactive {

    final CommandReactiveExecutor executorService;
    
    RedissonBaseReactive(CommandReactiveExecutor executorService) {
        this.executorService = executorService;
    }
    
    public static byte[] toByteArray(ByteBuffer buffer) {
        byte[] dst = new byte[buffer.remaining()];
        int pos = buffer.position();
        buffer.get(dst);
        buffer.position(pos);
        return dst;
    }
    
    RFuture<String> toStringFuture(RFuture<Void> f) {
        RPromise<String> promise = new RedissonPromise<>();
        f.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }
                
                promise.trySuccess("OK");
            }
        });
        return promise;
    }
    
    <T> Mono<T> execute(RedisClusterNode node, RedisCommand<T> command, Object... params) {
        MasterSlaveEntry entry = getEntry(node);
        return executorService.reactive(() -> {
            return executorService.writeAsync(entry, StringCodec.INSTANCE, command, params);
        });
    }

    MasterSlaveEntry getEntry(RedisClusterNode node) {
        MasterSlaveEntry entry = executorService.getConnectionManager().getEntry(new InetSocketAddress(node.getHost(), node.getPort()));
        return entry;
    }
    
    <V, T> Flux<T> execute(Publisher<V> commands, Function<V, Publisher<T>> mapper) {
        Flux<V> s = Flux.from(commands);
        return s.concatMap(mapper);
    }
    
    <T> Mono<T> write(byte[] key, Codec codec, RedisCommand<?> command, Object... params) {
        Mono<T> f = executorService.reactive(() -> {
            return executorService.writeAsync(key, codec, command, params);
        });
        return f.onErrorMap(e -> new RedisSystemException(e.getMessage(), e));
    }
    
    <T> Mono<T> read(byte[] key, Codec codec, RedisCommand<?> command, Object... params) {
        Mono<T> f = executorService.reactive(() -> {
            return executorService.readAsync(key, codec, command, params);
        });
        return f.onErrorMap(e -> new RedisSystemException(e.getMessage(), e));
    }

    
}
