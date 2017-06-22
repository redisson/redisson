/**
 * Copyright 2016 Nikita Koksharov
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
import org.redisson.api.map.MapWriter;

/**
 * RMap options object.
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
    private WriteMode writeMode;
    
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
     * Sets map writer object used for write-through operations.
     * 
     * @param writer object
     * @param writeMode for writer
     * @return MapOptions instance
     */
    public MapOptions<K, V> writer(MapWriter<K, V> writer, WriteMode writeMode) {
        this.writer = writer;
        this.writeMode = writeMode;
        return this;
    }
    public MapWriter<K, V> getWriter() {
        return writer;
    }
    public WriteMode getWriteMode() {
        return writeMode;
    }
    
    /**
     * Sets map reader object used for write-through operations.
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
    
}
