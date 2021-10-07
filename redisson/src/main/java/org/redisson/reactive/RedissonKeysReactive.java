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
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.redisson.RedissonKeys;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.connection.MasterSlaveEntry;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonKeysReactive {

    private final CommandReactiveExecutor commandExecutor;

    private final RedissonKeys instance;

    public RedissonKeysReactive(CommandReactiveExecutor commandExecutor) {
        super();
        instance = new RedissonKeys(commandExecutor);
        this.commandExecutor = commandExecutor;
    }

    public Flux<String> getKeys() {
        return getKeysByPattern(null);
    }

    public Flux<String> getKeys(int count) {
        return getKeysByPattern(null, count);
    }

    public Flux<String> getKeysByPattern(String pattern) {
        return getKeysByPattern(pattern, 10);
    }
    
    public Flux<String> getKeysByPattern(String pattern, int count) {
        List<Publisher<String>> publishers = new ArrayList<Publisher<String>>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            publishers.add(createKeysIterator(entry, pattern, count));
        }
        return Flux.merge(publishers);
    }

    private Flux<String> createKeysIterator(final MasterSlaveEntry entry, final String pattern, final int count) {
        return Flux.create(emitter -> emitter.onRequest(new IteratorConsumer<String>(emitter) {

            @Override
            protected boolean tryAgain() {
                return false;
            }

            @Override
            protected RFuture<ScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return instance.scanIteratorAsync(client, entry, nextIterPos, pattern, count);
            }
        }));
    }

}
