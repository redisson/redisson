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
package org.redisson.api.map;

import org.redisson.api.MapOptions;
import org.redisson.connection.ServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class RetryableMapWriterAsync<K, V> implements MapWriterAsync<K, V> {

    private static final Logger log = LoggerFactory.getLogger(RetryableMapWriterAsync.class);

    private final MapOptions<K, V> options;

    private final MapWriterAsync<K, V> mapWriterAsync;

    private ServiceManager serviceManager;

    public void setServiceManager(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    public RetryableMapWriterAsync(MapOptions<K, V> options, MapWriterAsync<K, V> mapWriterAsync) {
        this.options = options;
        this.mapWriterAsync = mapWriterAsync;
    }

    @Override
    public CompletionStage<Void> write(Map<K, V> addedMap) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        retryWrite(Math.max(1, options.getWriterRetryAttempts()), addedMap, result);
        return result;
    }

    private void retryWrite(int leftAttempts, Map<K, V> addedMap, CompletableFuture<Void> result) {
        mapWriterAsync.write(addedMap).whenComplete((x, e) -> {
                    if (e == null) {
                        result.complete(null);
                        return;
                    }

                    if (leftAttempts - 1 <= 0) {
                        result.completeExceptionally(e);
                        return;
                    }

                    if (serviceManager == null) {
                        log.warn("The serviceManager is null, so cannot retry writing keys: {}", addedMap);
                        result.completeExceptionally(e);
                        return;
                    }

                    log.warn("Unable to add keys: {}, will retry after {}ms", addedMap, options.getWriterRetryInterval(), e);
                    serviceManager.newTimeout(t -> retryWrite(leftAttempts - 1, addedMap, result),
                            options.getWriterRetryInterval(), TimeUnit.MILLISECONDS);
                }
        );
    }

    @Override
    public CompletionStage<Void> delete(Collection<K> keys) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        retryDelete(Math.max(1, options.getWriterRetryAttempts()), keys, result);
        return result;
    }

    private void retryDelete(int leftAttempts, Collection<K> keys, CompletableFuture<Void> result) {
        mapWriterAsync.delete(keys).whenComplete((x, e) -> {
                    if (e == null) {
                        result.complete(null);
                        return;
                    }

                    if (leftAttempts - 1 <= 0) {
                        result.completeExceptionally(e);
                        return;
                    }

                    if (serviceManager == null) {
                        log.warn("The serviceManager is null so cannot retry deleting keys: {}", keys);
                        result.completeExceptionally(e);
                        return;
                    }

                    log.warn("Unable to delete keys: {}, will retry after {}ms", keys, options.getWriterRetryInterval(), e);
                    serviceManager.newTimeout(t -> retryDelete(leftAttempts - 1, keys, result),
                            options.getWriterRetryInterval(), TimeUnit.MILLISECONDS);
                }
        );
    }


}
