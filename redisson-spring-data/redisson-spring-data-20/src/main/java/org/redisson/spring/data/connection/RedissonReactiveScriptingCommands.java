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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.redisson.SlotCallback;
import org.redisson.api.RFuture;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
            RFuture<Void> f = executorService.writeAllAsync(RedisCommands.SCRIPT_FLUSH);
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
            return executorService.writeAllAsync(StringCodec.INSTANCE, RedisCommands.SCRIPT_LOAD, new SlotCallback<String, String>() {
                volatile String result;
                @Override
                public void onSlotResult(String result) {
                    this.result = result;
                }
                
                @Override
                public String onFinish() {
                    return result;
                }
            }, toByteArray(script));
        });
    }

    @Override
    public Flux<Boolean> scriptExists(List<String> scriptShas) {
        Mono<List<Boolean>> m = executorService.reactive(() -> {
                return executorService.writeAllAsync(RedisCommands.SCRIPT_EXISTS, new SlotCallback<List<Boolean>, List<Boolean>>() {
                
                List<Boolean> result = new ArrayList<Boolean>(scriptShas.size());
                
                @Override
                public synchronized void onSlotResult(List<Boolean> result) {
                    for (int i = 0; i < result.size(); i++) {
                        if (this.result.size() == i) {
                            this.result.add(false);
                        }
                        this.result.set(i, this.result.get(i) | result.get(i));
                    }
                }
    
                @Override
                public List<Boolean> onFinish() {
                    return result;
                }
            }, scriptShas.toArray());
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
            c = org.redisson.api.RScript.ReturnType.MULTI.getCommand();
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
                if (((List) e).get(0).getClass().isArray()) {
                    return ((List<byte[]>)e).stream().map(v -> ByteBuffer.wrap(v)).collect(Collectors.toList());
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
