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
package org.redisson.spring.data.connection;

import org.redisson.api.RFuture;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveScriptingCommands extends RedissonBaseReactive implements ReactiveScriptingCommands {

    RedissonReactiveScriptingCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Mono<String> scriptFlush() {
        return executorService.reactive(() -> {
            RFuture<Void> f = executorService.writeAllVoidAsync(RedisCommands.SCRIPT_FLUSH);
            return toStringFuture(f);
        });
    }

    @Override
    public Mono<String> scriptKill() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<String> scriptLoad(ByteBuffer script) {
        return executorService.reactive(() -> {
            List<CompletableFuture<String>> futures = executorService.executeAllAsync(RedisCommands.SCRIPT_LOAD, (Object)toByteArray(script));
            CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            CompletableFuture<String> s = f.thenApply(r -> futures.get(0).getNow(null));
            return new CompletableFutureWrapper<>(s);
        });
    }

    @Override
    public Flux<Boolean> scriptExists(List<String> scriptShas) {
        Mono<List<Boolean>> m = executorService.reactive(() -> {
            List<CompletableFuture<List<Boolean>>> futures = executorService.writeAllAsync(RedisCommands.SCRIPT_EXISTS, scriptShas.toArray());
            CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            CompletableFuture<List<Boolean>> s = f.thenApply(r -> {
                List<Boolean> result = futures.get(0).getNow(new ArrayList<>());
                for (CompletableFuture<List<Boolean>> future : futures.subList(1, futures.size())) {
                    List<Boolean> l = future.getNow(new ArrayList<>());
                    for (int i = 0; i < l.size(); i++) {
                        result.set(i, result.get(i) | l.get(i));
                    }
                }
                return result;
            });
            return new CompletableFutureWrapper<>(s);
        });
        return m.flatMapMany(v -> Flux.fromIterable(v));
    }

    protected RedisCommand<?> toCommand(ReturnType returnType, String name) {
        RedisCommand<?> c = null; 
        if (returnType == ReturnType.BOOLEAN) {
            c = org.redisson.api.RScript.ReturnType.BOOLEAN.getCommand();
        } else if (returnType == ReturnType.INTEGER) {
            c = org.redisson.api.RScript.ReturnType.INTEGER.getCommand();
        } else if (returnType == ReturnType.MULTI) {
            c = org.redisson.api.RScript.ReturnType.LIST.getCommand();
            return new RedisCommand(c, name, new BinaryConvertor());
        } else if (returnType == ReturnType.STATUS) {
            c = org.redisson.api.RScript.ReturnType.STATUS.getCommand();
        } else if (returnType == ReturnType.VALUE) {
            c = org.redisson.api.RScript.ReturnType.VALUE.getCommand();
            return new RedisCommand(c, name, new BinaryConvertor());
        }
        return new RedisCommand(c, name);
    }
    
    @Override
    public <T> Flux<T> eval(ByteBuffer script, ReturnType returnType, int numKeys, ByteBuffer... keysAndArgs) {
        RedisCommand<?> c = toCommand(returnType, "EVAL");
        List<Object> params = new ArrayList<Object>();
        params.add(toByteArray(script));
        params.add(numKeys);
        params.addAll(Arrays.stream(keysAndArgs).map(m -> toByteArray(m)).collect(Collectors.toList()));
        Mono<T> m = write(null, ByteArrayCodec.INSTANCE, c, params.toArray());
        return convert(m);
    }

    protected <T> Flux<T> convert(Mono<T> m) {
        return (Flux<T>) m.map(e -> {
            if (e.getClass().isArray()) {
                return ByteBuffer.wrap((byte[])e);
            }
            if (e instanceof List) {
                List l = (List) e;
                if (!l.isEmpty()) {
                    for (int i = 0; i < l.size(); i++) {
                        if (l.get(i).getClass().isArray()) {
                            l.set(i, ByteBuffer.wrap((byte[])l.get(i)));
                        }
                    }
                    return l;
                }
            }
            return e;
        }).flux();
    }

    @Override
    public <T> Flux<T> evalSha(String scriptSha, ReturnType returnType, int numKeys, ByteBuffer... keysAndArgs) {
        RedisCommand<?> c = toCommand(returnType, "EVALSHA");
        List<Object> params = new ArrayList<Object>();
        params.add(scriptSha);
        params.add(numKeys);
        params.addAll(Arrays.stream(keysAndArgs).map(m -> toByteArray(m)).collect(Collectors.toList()));
        Mono<T> m = write(null, ByteArrayCodec.INSTANCE, c, params.toArray());
        return convert(m);
    }

}
