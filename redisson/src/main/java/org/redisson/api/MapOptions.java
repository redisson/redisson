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
package org.redisson.api;

import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapLoaderAsync;
import org.redisson.api.map.MapWriter;
import org.redisson.api.map.MapWriterAsync;

/**
 * Configuration for Map object.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class MapOptions<K, V> {
    
    public enum WriteMode {
        
        /**
         * In write behind mode all data written in map object 
         * also written using MapWriter in asynchronous mode.
         */
        WRITE_BEHIND,
        
        /**
         * In write through mode all write operations for map object 
         * are synchronized with MapWriter write operations.
         * If MapWriter throws an error then it will be re-thrown to Map operation caller.
         */
        WRITE_THROUGH
        
    }
    
    private MapLoader<K, V> loader;
    private MapWriter<K, V> writer;
    private MapWriterAsync<K, V> writerAsync;

    private MapLoaderAsync<K, V> loaderAsync;

    private WriteMode writeMode = WriteMode.WRITE_THROUGH;
    private int writeBehindBatchSize = 50;
    private int writeBehindDelay = 1000;
    
    protected MapOptions() {
    }
    
    protected MapOptions(MapOptions<K, V> copy) {
    }
    
    /**
     * Creates a new instance of MapOptions with default options.
     * <p>
     * This is equivalent to:
     * <pre>
     *     new MapOptions()
     *      .writer(null, null).loader(null);
     * </pre>
     * 
     * @param <K> key type
     * @param <V> value type
     * 
     * @return MapOptions instance
     * 
     */
    public static <K, V> MapOptions<K, V> defaults() {
        return new MapOptions<K, V>();
    }
    
    /**
     * Defines {@link MapWriter} object which is invoked during write operation.
     * 
     * @param writer object
     * @return MapOptions instance
     */
    public MapOptions<K, V> writer(MapWriter<K, V> writer) {
        this.writer = writer;
        return this;
    }
    public MapWriter<K, V> getWriter() {
        return writer;
    }

    /**
     * Defines {@link MapWriterAsync} object which is invoked during write operation.
     *
     * @param writer object
     * @return MapOptions instance
     */
    public MapOptions<K, V> writerAsync(MapWriterAsync<K, V> writer) {
        this.writerAsync = writer;
        return this;
    }
    public MapWriterAsync<K, V> getWriterAsync() {
        return writerAsync;
    }

    /**
     * Sets write behind tasks batch size. 
     * All updates accumulated into a batch of specified size and written with {@link MapWriter}.
     * <p>
     * Default is <code>50</code>
     * 
     * @param writeBehindBatchSize - size of batch
     * @return MapOptions instance
     */
    public MapOptions<K, V> writeBehindBatchSize(int writeBehindBatchSize) {
        this.writeBehindBatchSize = writeBehindBatchSize;
        return this;
    }
    public int getWriteBehindBatchSize() {
        return writeBehindBatchSize;
    }
    
    /**
     * Sets write behind tasks execution delay.
     * All updates written with {@link MapWriter} and lag not more than specified delay.
     * <p>
     * Default is <code>1000</code> milliseconds
     * 
     * @param writeBehindDelay - delay in milliseconds
     * @return MapOptions instance
     */
    public MapOptions<K, V> writeBehindDelay(int writeBehindDelay) {
        this.writeBehindDelay = writeBehindDelay;
        return this;
    }
    public int getWriteBehindDelay() {
        return writeBehindDelay;
    }
    
    /**
     * Sets write mode. 
     * <p>
     * Default is <code>{@link WriteMode#WRITE_THROUGH}</code>
     * 
     * @param writeMode - write mode
     * @return MapOptions instance
     */
    public MapOptions<K, V> writeMode(WriteMode writeMode) {
        this.writeMode = writeMode;
        return this;
    }
    public WriteMode getWriteMode() {
        return writeMode;
    }
    
    /**
     * Sets {@link MapLoader} object.
     * 
     * @param loader object
     * @return MapOptions instance
     */
    public MapOptions<K, V> loader(MapLoader<K, V> loader) {
        this.loader = loader;
        return this;
    }
    public MapLoader<K, V> getLoader() {
        return loader;
    }

    /**
     * Sets {@link MapLoaderAsync} object.
     *
     * @param loaderAsync object
     * @return MapOptions instance
     */
    public MapOptions<K, V> loaderAsync(MapLoaderAsync<K, V> loaderAsync) {
        this.loaderAsync = loaderAsync;
        return this;
    }
    public MapLoaderAsync<K, V> getLoaderAsync() {
        return loaderAsync;
    }
}
