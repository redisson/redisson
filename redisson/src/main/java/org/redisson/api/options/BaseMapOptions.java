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
package org.redisson.api.options;

import org.redisson.api.map.*;
import org.redisson.client.codec.Codec;

import java.time.Duration;

/**
 *
 * @author Nikita Koksharov
 *
 */
class BaseMapOptions<T extends ExMapOptions<T, K, V>, K, V> extends BaseOptions<T, Codec>
                                                            implements ExMapOptions<T, K, V> {

    private int writeRetryAttempts = 0;
    private MapWriter<K, V> writer;
    private MapWriterAsync<K, V> writerAsync;
    private int writeBehindBatchSize;
    private int writeBehindDelay;
    private WriteMode writeMode;
    private long writeRetryInterval;
    private MapLoader<K, V> loader;
    private MapLoaderAsync<K, V> loaderAsync;

    public T writer(MapWriter<K, V> writer) {
        if (writer != null) {
            org.redisson.api.MapOptions<K, V> options = (org.redisson.api.MapOptions<K, V>) org.redisson.api.MapOptions.defaults()
                    .writerRetryInterval(Duration.ofMillis(getWriteRetryInterval()));
            if (getWriteRetryAttempts() > 0) {
                options.writerRetryAttempts(getWriteRetryAttempts());
            }

            this.writer = new RetryableMapWriter<>(options, writer);
        }
        return (T) this;
    }
    public MapWriter<K, V> getWriter() {
        return writer;
    }

    public T writerAsync(MapWriterAsync<K, V> writer) {
        if (writer != null) {
            org.redisson.api.MapOptions<K, V> options = (org.redisson.api.MapOptions<K, V>) org.redisson.api.MapOptions.defaults()
                    .writerRetryInterval(Duration.ofMillis(getWriteRetryInterval()));

            if (getWriteRetryAttempts() > 0) {
                options.writerRetryAttempts(getWriteRetryAttempts());
            }

            this.writerAsync = new RetryableMapWriterAsync<>(options, writer);
        }
        return (T) this;
    }
    public MapWriterAsync<K, V> getWriterAsync() {
        return writerAsync;
    }

    public T writeBehindBatchSize(int writeBehindBatchSize) {
        this.writeBehindBatchSize = writeBehindBatchSize;
        return (T) this;
    }
    public int getWriteBehindBatchSize() {
        return writeBehindBatchSize;
    }

    public T writeBehindDelay(int writeBehindDelay) {
        this.writeBehindDelay = writeBehindDelay;
        return (T) this;
    }
    public int getWriteBehindDelay() {
        return writeBehindDelay;
    }

    public T writeMode(WriteMode writeMode) {
        this.writeMode = writeMode;
        return (T) this;
    }
    public WriteMode getWriteMode() {
        return writeMode;
    }

    public int getWriteRetryAttempts() {
        return writeRetryAttempts;
    }

    /**
     * Sets max retry attempts for {@link RetryableMapWriter} or {@link RetryableMapWriterAsync}
     *
     * @param writerRetryAttempts object
     * @return MapOptions instance
     */
    public T writeRetryAttempts(int writerRetryAttempts) {
        if (writerRetryAttempts <= 0){
            throw new IllegalArgumentException("writerRetryAttempts must be bigger than 0");
        }
        this.writeRetryAttempts = writerRetryAttempts;
        return (T) this;
    }

    public long getWriteRetryInterval() {
        return writeRetryInterval;
    }

    public T writeRetryInterval(Duration writerRetryInterval) {
        if (writerRetryInterval.isNegative()) {
            throw new IllegalArgumentException("writerRetryInterval must be positive");
        }
        this.writeRetryInterval = writerRetryInterval.toMillis();
        return (T) this;
    }

    /**
     * Sets {@link MapLoader} object.
     *
     * @param loader object
     * @return MapOptions instance
     */
    public T loader(MapLoader<K, V> loader) {
        this.loader = loader;
        return (T) this;
    }
    public MapLoader<K, V> getLoader() {
        return loader;
    }

    public T loaderAsync(MapLoaderAsync<K, V> loaderAsync) {
        this.loaderAsync = loaderAsync;
        return (T) this;
    }

    public MapLoaderAsync<K, V> getLoaderAsync() {
        return loaderAsync;
    }

}
