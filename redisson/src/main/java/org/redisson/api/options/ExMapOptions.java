/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
 * @param <T> returned object type
 * @param <K> type of key
 * @param <V> type of value
 */
public interface ExMapOptions<T extends ExMapOptions<T, K, V>, K, V> extends CodecOptions<T, Codec> {

    /**
     * Defines {@link MapWriter} object which is invoked during write operation.
     *
     * @param writer object
     * @return MapOptions instance
     */
    T writer(MapWriter<K, V> writer);

    /**
     * Defines {@link MapWriterAsync} object which is invoked during write operation.
     *
     * @param writer object
     * @return MapOptions instance
     */
    T writerAsync(MapWriterAsync<K, V> writer);
    /**
     * Sets write behind tasks batch size.
     * All updates accumulated into a batch of specified size and written with {@link MapWriter}.
     * <p>
     * Default is <code>50</code>
     *
     * @param writeBehindBatchSize size of batch
     * @return MapOptions instance
     */
    T writeBehindBatchSize(int writeBehindBatchSize);

    /**
     * Sets write behind tasks execution delay.
     * All updates written with {@link MapWriter} and lag not more than specified delay.
     * <p>
     * Default is <code>1000</code> milliseconds
     *
     * @param writeBehindDelay delay in milliseconds
     * @return MapOptions instance
     */
    T writeBehindDelay(int writeBehindDelay);

    /**
     * Sets write mode.
     * <p>
     * Default is <code>{@link WriteMode#WRITE_THROUGH}</code>
     *
     * @param writeMode write mode
     * @return MapOptions instance
     */
    T writeMode(WriteMode writeMode);

    /**
     * Sets max write retry attempts
     *
     * @param writerRetryAttempts object
     * @return MapOptions instance
     */
    T writeRetryAttempts(int writerRetryAttempts);

    /**
     * Sets write retry interval
     *
     * @param writerRetryInterval {@link Duration}
     * @return MapOptions instance
     */
    T writeRetryInterval(Duration writerRetryInterval);

    /**
     * Sets {@link MapLoader} object.
     *
     * @param loader object
     * @return MapOptions instance
     */
    T loader(MapLoader<K, V> loader);

    /**
     * Sets {@link MapLoaderAsync} object.
     *
     * @param loaderAsync object
     * @return MapOptions instance
     */
    T loaderAsync(MapLoaderAsync<K, V> loaderAsync);

}
