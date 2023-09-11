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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class RetryableMapWriterAsync<K, V> implements MapWriterAsync<K, V> {

    private static final Logger log = LoggerFactory.getLogger(RetryableMapWriterAsync.class);

    private final MapOptions<K, V> options;
    private final MapWriterAsync<K, V> mapWriterAsync;

    //store entries no need to be retried
    private final Map<K, V> noRetriesForWrite = new ConcurrentHashMap<>();

    public RetryableMapWriterAsync(MapOptions<K, V> options, MapWriterAsync<K, V> mapWriterAsync) {
        this.options = options;
        this.mapWriterAsync = mapWriterAsync;
    }

    @Override
    public CompletionStage<Void> write(Map<K, V> addedMap) {
        //execute at least once
        int leftAddAttempts = Math.max(1, options.getWriterRetryAttempts());
        while (leftAddAttempts > 0) {
            try {
                //remove successful part
                if (!noRetriesForWrite.isEmpty()) {
                    noRetriesForWrite.forEach(addedMap::remove);
                    noRetriesForWrite.clear();
                }

                //do write
                return mapWriterAsync.write(addedMap);
            } catch (Exception exception) {
                if (--leftAddAttempts == 0) {
                    throw exception;
                } else {
                    log.warn("Unable to add keys: {}, will retry after {}ms", addedMap, options.getWriterRetryInterval(), exception);
                    try {
                        Thread.sleep(options.getWriterRetryInterval());
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        }

        //unreachable
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> delete(Collection<K> keys) {
        return mapWriterAsync.delete(keys);
    }


   /* public void writeSuccess(Map<K, V> noRetries) {
        noRetriesForWrite.putAll(noRetries);
    }

    public void writeSuccess(Map.Entry<K, V> noRetry) {
        noRetriesForWrite.put(noRetry.getKey(), noRetry.getValue());
    }*/
}
