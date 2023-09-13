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
package org.redisson.api.map;

import org.redisson.api.MapOptions;
import org.redisson.connection.ServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class RetryableMapWriterAsync<K, V> implements MapWriterAsync<K, V> {

    private static final Logger log = LoggerFactory.getLogger(RetryableMapWriterAsync.class);

    private final MapOptions<K, V> options;

    private final MapWriterAsync<K, V> mapWriterAsync;

    private ServiceManager serviceManager;

    public RetryableMapWriterAsync(MapOptions<K, V> options, MapWriterAsync<K, V> mapWriterAsync) {
        this.options = options;
        this.mapWriterAsync = mapWriterAsync;
    }

    @Override
    public CompletionStage<Void> write(Map<K, V> addedMap) {
        return retryWrite(Math.max(1, options.getWriterRetryAttempts()), new LinkedHashMap<>(addedMap));
    }

    private CompletableFuture<Void> retryWrite(int leftAttempts, Map<K, V> addedMap) {
        return CompletableFuture.runAsync(() -> {
            try {
                //do write
                mapWriterAsync.write(addedMap).toCompletableFuture().join();
            } catch (Exception exception) {
                if (leftAttempts - 1 == 0) {
                    throw exception;
                } else {
                    //only need serviceManager when exception happened
                    Objects.requireNonNull(serviceManager);
                    log.warn("Unable to add keys: {}, will retry after {}ms", addedMap, options.getWriterRetryInterval(), exception);
                    serviceManager.newTimeout(t -> retryWrite(leftAttempts - 1, addedMap).toCompletableFuture().join(),
                            options.getWriterRetryInterval(), TimeUnit.MILLISECONDS);
                }
            }
        });
    }

    @Override
    public CompletionStage<Void> delete(Collection<K> keys) {
        return CompletableFuture.runAsync(() -> {
            //execute at least once
            int leftDeleteAttempts = Math.max(1, options.getWriterRetryAttempts());
            while (leftDeleteAttempts > 0) {
                try {
                    //do delete
                    mapWriterAsync.delete(keys).toCompletableFuture().join();
                    break;
                } catch (Exception exception) {
                    if (--leftDeleteAttempts == 0) {
                        throw exception;
                    } else {
                        log.warn("Unable to delete keys: {}, will retry after {}ms", keys, options.getWriterRetryInterval(), exception);
                        try {
                            Thread.sleep(options.getWriterRetryInterval());
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
            }
        });
    }

    public RetryableMapWriterAsync<K, V> withRetryManager(ServiceManager serviceManager) {
        if (this.serviceManager == null) {
            this.serviceManager = serviceManager;
        }
        return this;
    }
}
