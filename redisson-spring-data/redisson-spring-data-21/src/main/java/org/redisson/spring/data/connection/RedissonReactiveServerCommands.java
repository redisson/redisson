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

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.redisson.SlotCallback;
import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.TimeLongObjectDecoder;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveServerCommands;
import org.springframework.data.redis.core.types.RedisClientInfo;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveServerCommands extends RedissonBaseReactive implements ReactiveServerCommands {

    RedissonReactiveServerCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    static final RedisStrictCommand<String> BGREWRITEAOF = new RedisStrictCommand<String>("BGREWRITEAOF");
    
    @Override
    public Mono<String> bgReWriteAof() {
        return write(null, StringCodec.INSTANCE, BGREWRITEAOF);
    }

    static final RedisStrictCommand<String> BGSAVE = new RedisStrictCommand<String>("BGSAVE");
    
    @Override
    public Mono<String> bgSave() {
        return write(null, StringCodec.INSTANCE, BGSAVE);
    }

    @Override
    public Mono<Long> lastSave() {
        return write(null, StringCodec.INSTANCE, RedisCommands.LASTSAVE);
    }

    static final RedisStrictCommand<String> SAVE = new RedisStrictCommand<String>("SAVE");

    @Override
    public Mono<String> save() {
        return write(null, StringCodec.INSTANCE, SAVE);
    }

    @Override
    public Mono<Long> dbSize() {
        return executorService.reactive(() -> {
            return executorService.readAllAsync(RedisCommands.DBSIZE, new SlotCallback<Long, Long>() {
                AtomicLong results = new AtomicLong();
                @Override
                public void onSlotResult(Long result) {
                    results.addAndGet(result);
                }
                
                @Override
                public Long onFinish() {
                    return results.get();
                }
            });
        });
    }
    
    private static final RedisStrictCommand<String> FLUSHDB = new RedisStrictCommand<String>("FLUSHDB");

    @Override
    public Mono<String> flushDb() {
        return executorService.reactive(() -> {
            RFuture<Void> f = executorService.writeAllAsync(FLUSHDB);
            return toStringFuture(f);
        });
    }

    private static final RedisStrictCommand<String> FLUSHALL = new RedisStrictCommand<String>("FLUSHALL");

    @Override
    public Mono<String> flushAll() {
        return executorService.reactive(() -> {
            RFuture<Void> f = executorService.writeAllAsync(FLUSHALL);
            return toStringFuture(f);
        });
    }

    static final RedisStrictCommand<Properties> INFO_DEFAULT = new RedisStrictCommand<Properties>("INFO", "DEFAULT", new PropertiesDecoder());
    static final RedisStrictCommand<Properties> INFO = new RedisStrictCommand<Properties>("INFO", new PropertiesDecoder());
    
    @Override
    public Mono<Properties> info() {
        return read(null, StringCodec.INSTANCE, INFO_DEFAULT);
    }

    @Override
    public Mono<Properties> info(String section) {
        return read(null, StringCodec.INSTANCE, INFO, section);
    }

    static final RedisStrictCommand<Properties> CONFIG_GET = new RedisStrictCommand<Properties>("CONFIG", "GET", new PropertiesListDecoder());
    
    @Override
    public Mono<Properties> getConfig(String pattern) {
        return read(null, StringCodec.INSTANCE, CONFIG_GET, pattern);
    }
    
    static final RedisStrictCommand<String> CONFIG_SET = new RedisStrictCommand<String>("CONFIG", "SET");

    @Override
    public Mono<String> setConfig(String param, String value) {
        return write(null, StringCodec.INSTANCE, CONFIG_SET, param, value);
    }
    
    static final RedisStrictCommand<String> CONFIG_RESETSTAT = new RedisStrictCommand<String>("CONFIG", "RESETSTAT");

    @Override
    public Mono<String> resetConfigStats() {
        return write(null, StringCodec.INSTANCE, CONFIG_RESETSTAT);
    }

    static final RedisStrictCommand<Long> TIME = new RedisStrictCommand<Long>("TIME", new TimeLongObjectDecoder());
    
    @Override
    public Mono<Long> time() {
        return read(null, LongCodec.INSTANCE, TIME);
    }

    @Override
    public Mono<String> killClient(String host, int port) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<String> setClientName(String name) {
        throw new UnsupportedOperationException("Should be defined through Redisson Config object");
    }

    @Override
    public Mono<String> getClientName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Flux<RedisClientInfo> getClientList() {
        throw new UnsupportedOperationException();
    }

}
