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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronous Map writer used for write-through operations.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public abstract class MapWriterAsync<K, V> extends RetryableWriter {

    private final Map<K, V> noRetriesForWrite = new ConcurrentHashMap<>();
    private final Collection<K> noRetriesForDelete = Collections.synchronizedList(new ArrayList<>());


    public MapWriterAsync() {
        super();
    }

    public MapWriterAsync(int retryAttempts) {
        super(retryAttempts);
    }

    public MapWriterAsync(int retryAttempts, long retryInterval) {
        super(retryAttempts, retryInterval);
    }

    public abstract CompletionStage<Void> write(Map<K, V> map);

    public abstract CompletionStage<Void> delete(Collection<K> keys);


    public void writeSuccess(Map<K, V> noRetries) {
        noRetriesForWrite.putAll(noRetries);
    }

    public void writeSuccess(Map.Entry<K, V> noRetry) {
        noRetriesForWrite.put(noRetry.getKey(), noRetry.getValue());
    }

    public void deleteSuccess(K noRetry) {
        noRetriesForDelete.add(noRetry);
    }

    public void deleteSuccess(Collection<K> noRetries) {
        noRetriesForDelete.addAll(noRetries);
    }

    public Map<K, V> getNoRetriesForWrite() {
        return noRetriesForWrite;
    }

    public Collection<K> getNoRetriesForDelete() {
        return noRetriesForDelete;
    }
}
