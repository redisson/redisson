/**
 * Copyright 2018 Nikita Koksharov
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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis Stream implementation.
 * <p>
 * Requires <b>Redis 5.0.0 and higher.</b>
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RStream<K, V> extends RStreamAsync<K, V>, RExpirable {

    /**
     * Returns number of entries in stream
     * 
     * @return size of stream
     */
    long size();

    /**
     * Appends a new entry and returns generated Stream ID
     * 
     * @param key - key of entry
     * @param value - value of entry
     * @return Stream ID
     */
    StreamId add(K key, V value);
    
    /**
     * Appends a new entry by specified Stream ID
     * 
     * @param id - Stream ID
     * @param key - key of entry
     * @param value - value of entry
     */
    void add(StreamId id, K key, V value);
    
    /**
     * Appends a new entry and returns generated Stream ID.
     * Trims stream to a specified <code>trimLen</code> size.
     * If <code>trimStrict</code> is <code>false</code> then trims to few tens of entries more than specified length to trim.
     * 
     * @param key - key of entry
     * @param value - value of entry
     * @param trimLen - length to trim
     * @param trimStrict - if <code>false</code> then trims to few tens of entries more than specified length to trim
     * @return Stream ID
     */
    StreamId add(K key, V value, int trimLen, boolean trimStrict);

    /**
     * Appends a new entry by specified Stream ID.
     * Trims stream to a specified <code>trimLen</code> size.
     * If <code>trimStrict</code> is <code>false</code> then trims to few tens of entries more than specified length to trim.
     * 
     * @param id - Stream ID
     * @param key - key of entry
     * @param value - value of entry
     * @param trimLen - length to trim
     * @param trimStrict - if <code>false</code> then trims to few tens of entries more than specified length to trim
     */
    void add(StreamId id, K key, V value, int trimLen, boolean trimStrict);
    
    /**
     * Appends new entries and returns generated Stream ID
     * 
     * @param entries - entries to add
     * @return Stream ID
     */
    StreamId addAll(Map<K, V> entries);
    
    /**
     * Appends new entries by specified Stream ID
     * 
     * @param id - Stream ID
     * @param entries - entries to add
     */
    void addAll(StreamId id, Map<K, V> entries);
    
    /**
     * Appends new entries and returns generated Stream ID.
     * Trims stream to a specified <code>trimLen</code> size.
     * If <code>trimStrict</code> is <code>false</code> then trims to few tens of entries more than specified length to trim.
     * 
     * @param entries - entries to add
     * @param trimLen - length to trim
     * @param trimStrict - if <code>false</code> then trims to few tens of entries more than specified length to trim
     * @return Stream ID
     */
    StreamId addAll(Map<K, V> entries, int trimLen, boolean trimStrict);
    
    /**
     * Appends new entries by specified Stream ID.
     * Trims stream to a specified <code>trimLen</code> size.
     * If <code>trimStrict</code> is <code>false</code> then trims to few tens of entries more than specified length to trim.
     * 
     * @param id - Stream ID
     * @param entries - entries to add
     * @param trimLen - length to trim
     * @param trimStrict - if <code>false</code> then trims to few tens of entries more than specified length to trim
     */
    void addAll(StreamId id, Map<K, V> entries, int trimLen, boolean trimStrict);
    
    /**
     * Read stream data by specified collection of Stream IDs.
     * 
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Map<StreamId, Map<K, V>> read(StreamId ... ids);

    /**
     * Read stream data by specified collection of Stream IDs.
     * 
     * @param count - stream data size limit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Map<StreamId, Map<K, V>> read(int count, StreamId ... ids);
    
    /**
     * Read stream data by specified collection of Stream IDs. 
     * Wait for stream data availability for specified <code>timeout</code> interval.
     * 
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Map<StreamId, Map<K, V>> read(long timeout, TimeUnit unit, StreamId ... ids);

    /**
     * Read stream data by specified collection of Stream IDs. 
     * Wait for stream data availability for specified <code>timeout</code> interval.
     * 
     * @param count - stream data size limit
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Map<StreamId, Map<K, V>> read(int count, long timeout, TimeUnit unit, StreamId ... ids);

    /**
     * Read stream data by specified stream name including this stream.
     * 
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(StreamId id, String name2, StreamId id2);

    /**
     * Read stream data by specified stream names including this stream.
     * 
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @param name3 - name of third stream
     * @param id3 - id of third stream
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(StreamId id, String name2, StreamId id2, String name3, StreamId id3);
    
    /**
     * Read stream data by specified stream id mapped by name including this stream.
     * 
     * @param id - id of this stream
     * @param nameToId - stream id mapped by name
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(StreamId id, Map<String, StreamId> nameToId);

    /**
     * Read stream data by specified stream name including this stream.
     * 
     * @param count - stream data size limit
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(int count, StreamId id, String name2, StreamId id2);

    /**
     * Read stream data by specified stream names including this stream.
     * 
     * @param count - stream data size limit
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @param name3 - name of third stream
     * @param id3 - id of third stream
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(int count, StreamId id, String name2, StreamId id2, String name3, StreamId id3);
    
    /**
     * Read stream data by specified stream id mapped by name including this stream.
     * 
     * @param count - stream data size limit
     * @param id - id of this stream
     * @param nameToId - stream id mapped by name
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(int count, StreamId id, Map<String, StreamId> nameToId);

    /**
     * Read stream data by specified stream name including this stream.
     * Wait for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamId id, String name2, StreamId id2);

    /**
     * Read stream data by specified stream names including this stream.
     * Wait for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @param name3 - name of third stream
     * @param id3 - id of third stream
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamId id, String name2, StreamId id2, String name3, StreamId id3);
    
    /**
     * Read stream data by specified stream id mapped by name including this stream.
     * Wait for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - id of this stream
     * @param nameToId - stream id mapped by name
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamId id, Map<String, StreamId> nameToId);

    /**
     * Read stream data by specified stream name including this stream.
     * Wait for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param count - stream data size limit
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamId id, String name2, StreamId id2);

    /**
     * Read stream data by specified stream names including this stream.
     * Wait for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param count - stream data size limit
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @param name3 - name of third stream
     * @param id3 - id of third stream
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamId id, String name2, StreamId id2, String name3, StreamId id3);
    
    /**
     * Read stream data by specified stream id mapped by name including this stream.
     * Wait for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param count - stream data size limit
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - id of this stream
     * @param nameToId - stream id mapped by name
     * @return stream data mapped by key and Stream ID
     */
    Map<String, Map<StreamId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamId id, Map<String, StreamId> nameToId);

    /**
     * Read stream data in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    Map<StreamId, Map<K, V>> range(StreamId startId, StreamId endId);
    
    /**
     * Read stream data in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    Map<StreamId, Map<K, V>> range(int count, StreamId startId, StreamId endId);
    
    /**
     * Read stream data in reverse order in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    Map<StreamId, Map<K, V>> rangeReversed(StreamId startId, StreamId endId);
    
    /**
     * Read stream data in reverse order in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    Map<StreamId, Map<K, V>> rangeReversed(int count, StreamId startId, StreamId endId);

}
