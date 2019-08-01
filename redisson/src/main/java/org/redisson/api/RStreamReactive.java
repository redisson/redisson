/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import reactor.core.publisher.Mono;

/**
 * Reactive interface for Redis Stream object.
 * <p>
 * Requires <b>Redis 5.0.0 and higher.</b>
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RStreamReactive<K, V> extends RExpirableReactive {

    /**
     * Creates consumer group by name.
     * 
     * @param groupName - name of group
     * @return void
     */
    Mono<Void> createGroup(String groupName);
    
    /**
     * Creates consumer group by name and stream id. 
     * Only new messages after defined stream <code>id</code> will be available for consumers of this group. 
     * <p>
     * {@link StreamMessageId#NEWEST} is used for messages arrived since the moment of group creating
     * 
     * @param groupName - name of group
     * @param id - stream id
     * @return void
     */
    Mono<Void> createGroup(String groupName, StreamMessageId id);
    
    /**
     * Removes group by name.
     * 
     * @param groupName - name of group
     * @return void
     */
    Mono<Void> removeGroup(String groupName);

    /**
     * Removes consumer of the group by name.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @return number of pending messages owned by consumer
     */
    Mono<Long> removeConsumer(String groupName, String consumerName);
    
    /**
     * Updates next message id delivered to consumers. 
     * 
     * @param groupName - name of group
     * @param id - Stream Message ID
     * @return void
     */
    Mono<Void> updateGroupMessageId(String groupName, StreamMessageId id);
    
    /**
     * Marks pending messages by group name and stream <code>ids</code> as correctly processed.
     * 
     * @param groupName - name of group
     * @param ids - stream ids
     * @return marked messages amount
     */
    Mono<Long> ack(String groupName, StreamMessageId... ids);
    
    /**
     * Returns common info about pending messages by group name.
     * 
     * @param groupName - name of group
     * @return result object
     */
    Mono<PendingResult> getPendingInfo(String groupName);

    /*
     * Use #getPendingInfo method
     */
    @Deprecated
    Mono<PendingResult> listPending(String groupName);

    /**
     * Returns list of pending messages by group name.
     * Limited by start stream id and end stream id and count.
     * <p>
     * {@link StreamMessageId#MAX} is used as max stream id
     * {@link StreamMessageId#MIN} is used as min stream id
     * 
     * @param groupName - name of group
     * @param startId - start stream id
     * @param endId - end stream id
     * @param count - amount of messages
     * @return list
     */
    Mono<List<PendingEntry>> listPending(String groupName, StreamMessageId startId, StreamMessageId endId, int count);
    
    /**
     * Returns list of pending messages by group name and consumer name.
     * Limited by start stream id and end stream id and count.
     * <p>
     * {@link StreamMessageId#MAX} is used as max stream id
     * {@link StreamMessageId#MIN} is used as min stream id
     * 
     * @param consumerName - name of consumer
     * @param groupName - name of group
     * @param startId - start stream id
     * @param endId - end stream id
     * @param count - amount of messages
     * @return list
     */
    Mono<List<PendingEntry>> listPending(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count);
    
    /**
     * Transfers ownership of pending messages by id to a new consumer 
     * by name if idle time of messages is greater than defined value. 
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param ids - stream ids
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> claim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId... ids);
    
    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code> and specified collection of Stream IDs.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMessageId... ids);
    
    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code> and specified collection of Stream IDs.
     * 
     * @param groupName - name of group
     * @param consumerName - consumer name
     * @param count - stream data size limit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, StreamMessageId... ids);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code> and specified collection of Stream IDs. 
     * Wait for stream data availability for specified <code>timeout</code> interval.
     * 
     * @param groupName - name of group
     * @param consumerName - consumer name
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId... ids);
    
    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code> and specified collection of Stream IDs. 
     * Wait for stream data availability for specified <code>timeout</code> interval.
     * 
     * @param groupName - name of group
     * @param consumerName - consumer name
     * @param count - stream data size limit
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId... ids);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param id - starting message id for this stream
     * @param nameToId - Stream Message ID mapped by stream name
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, StreamMessageId id, Map<String, StreamMessageId> nameToId);
    
    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param id - starting message id for this stream
     * @param nameToId - Stream Message ID mapped by stream name
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * Waits for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2 - starting message id for second stream
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id, String key2, StreamMessageId id2);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * Waits for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2 - starting message id for second stream
     * @param key3 - name of third stream
     * @param id3 - starting message id for third stream
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2, String key3, StreamMessageId id3);
    
    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * Waits for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - starting message id for this stream
     * @param nameToId - Stream Message ID mapped by stream name
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);
    
    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2 - starting message id for second stream
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2  - starting message id for second stream
     * @param key3 - name of third stream
     * @param id3  - starting message id for third stream
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2  - starting message id for second stream
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2 - starting message id for second stream
     * @param key3 - name of third stream
     * @param id3 - starting message id for third stream
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2,
            String key3, StreamMessageId id3);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * Waits for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2 - starting message id for second stream
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2);

    /**
     * Read stream data from <code>groupName</code> by <code>consumerName</code>, starting by specified message ids for this and other streams.
     * Waits for the first stream data availability for specified <code>timeout</code> interval.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2 - starting message id for second stream
     * @param key3 - name of third stream
     * @param id3 - starting message id for third stream
     * @return stream data mapped by key and Stream Message ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2, String key3, StreamMessageId id3);
    
    /**
     * Returns number of entries in stream
     * 
     * @return size of stream
     */
    Mono<Long> size();

    /**
     * Appends a new entry and returns generated Stream ID
     * 
     * @param key - key of entry
     * @param value - value of entry
     * @return Stream ID
     */
    Mono<StreamMessageId> add(K key, V value);
    
    /**
     * Appends a new entry by specified Stream ID
     * 
     * @param id - Stream ID
     * @param key - key of entry
     * @param value - value of entry
     * @return void
     */
    Mono<Void> add(StreamMessageId id, K key, V value);
    
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
    Mono<StreamMessageId> add(K key, V value, int trimLen, boolean trimStrict);

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
     * @return void
     */
    Mono<Void> add(StreamMessageId id, K key, V value, int trimLen, boolean trimStrict);
    
    /**
     * Appends new entries and returns generated Stream ID
     * 
     * @param entries - entries to add
     * @return Stream ID
     */
    Mono<StreamMessageId> addAll(Map<K, V> entries);
    
    /**
     * Appends new entries by specified Stream ID
     * 
     * @param id - Stream ID
     * @param entries - entries to add
     * @return void
     */
    Mono<Void> addAll(StreamMessageId id, Map<K, V> entries);
    
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
    Mono<StreamMessageId> addAll(Map<K, V> entries, int trimLen, boolean trimStrict);

    /**
     * Appends new entries by specified Stream ID.
     * Trims stream to a specified <code>trimLen</code> size.
     * If <code>trimStrict</code> is <code>false</code> then trims to few tens of entries more than specified length to trim.
     * 
     * @param id - Stream ID
     * @param entries - entries to add
     * @param trimLen - length to trim
     * @param trimStrict - if <code>false</code> then trims to few tens of entries more than specified length to trim
     * @return void
     */
    Mono<Void> addAll(StreamMessageId id, Map<K, V> entries, int trimLen, boolean trimStrict);
    
    /**
     * Read stream data by specified collection of Stream IDs.
     * 
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> read(StreamMessageId... ids);
    
    /**
     * Read stream data by specified collection of Stream IDs.
     * 
     * @param count - stream data size limit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> read(int count, StreamMessageId... ids);

    /**
     * Read stream data by specified collection of Stream IDs. 
     * Wait for stream data availability for specified <code>timeout</code> interval.
     * 
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamMessageId... ids);
    
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
    Mono<Map<StreamMessageId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamMessageId... ids);

    /**
     * Read stream data by specified stream name including this stream.
     * 
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @return stream data mapped by key and Stream ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(StreamMessageId id, String name2, StreamMessageId id2);

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
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /**
     * Read stream data by specified stream id mapped by name including this stream.
     * 
     * @param id - id of this stream
     * @param nameToId - stream id mapped by name
     * @return stream data mapped by key and Stream ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /**
     * Read stream data by specified stream name including this stream.
     * 
     * @param count - stream data size limit
     * @param id - id of this stream
     * @param name2 - name of second stream
     * @param id2 - id of second stream
     * @return stream data mapped by key and Stream ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(int count, StreamMessageId id, String name2, StreamMessageId id2);

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
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(int count, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /**
     * Read stream data by specified stream id mapped by name including this stream.
     * 
     * @param count - stream data size limit
     * @param id - id of this stream
     * @param nameToId - stream id mapped by name
     * @return stream data mapped by key and Stream ID
     */
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(int count, StreamMessageId id, Map<String, StreamMessageId> nameToId);

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
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2);

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
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
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
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);

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
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2);

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
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
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
    Mono<Map<String, Map<StreamMessageId, Map<K, V>>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);
    
    /**
     * Returns stream data in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> range(StreamMessageId startId, StreamMessageId endId);

    /**
     * Returns stream data in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> range(int count, StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in reverse order in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> rangeReversed(StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in reverse order in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    Mono<Map<StreamMessageId, Map<K, V>>> rangeReversed(int count, StreamMessageId startId, StreamMessageId endId);

    /**
     * Removes messages by id.
     * 
     * @param ids - id of messages to remove
     * @return deleted messages amount
     */
    Mono<Long> remove(StreamMessageId... ids);

    /**
     * Trims stream to specified size
     * 
     * @param size - new size of stream
     * @return number of deleted messages
     */
    Mono<Long> trim(int size);

    /**
     * Trims stream to few tens of entries more than specified length to trim.
     * 
     * @param size - new size of stream
     * @return number of deleted messages
     */
    Mono<Long> trimNonStrict(int size);

    /**
     * Returns information about this stream.
     * 
     * @return info object
     */
    Mono<StreamInfo<K, V>> getInfo();
    
    /**
     * Returns list of objects with information about groups belonging to this stream.
     * 
     * @return list of info objects 
     */
    Mono<List<StreamGroup>> listGroups();

    /**
     * Returns list of objects with information about group customers for specified <code>groupName</code>.
     * 
     * @param groupName - name of group
     * @return list of info objects
     */
    Mono<List<StreamConsumer>> listConsumers(String groupName);

    /**
     * Returns stream data of pending messages by group name.
     * Limited by start Stream Message ID and end Stream Message ID and count.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * 
     * @see #listPending
     * 
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param count - amount of messages
     * @return map
     */
    Mono<Map<StreamMessageId, Map<K, V>>> pendingRange(String groupName, StreamMessageId startId, StreamMessageId endId, int count);
    
    /**
     * Returns stream data of pending messages by group and customer name.
     * Limited by start Stream Message ID and end Stream Message ID and count.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * 
     * @see #listPending
     * 
     * @param consumerName - name of consumer
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param count - amount of messages
     * @return map
     */
    Mono<Map<StreamMessageId, Map<K, V>>> pendingRange(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count);
    
}
