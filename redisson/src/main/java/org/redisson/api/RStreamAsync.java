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

import org.redisson.api.stream.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Async interface for Redis Stream object.
 * <p>
 * Requires <b>Redis 5.0.0 and higher.</b>
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RStreamAsync<K, V> extends RExpirableAsync {

    /**
     * Creates consumer group.
     * <p>
     * Usage examples:
     * <pre>
     * StreamMessageId id = stream.createGroup(StreamCreateGroupArgs.name("test").id(id).makeStream());
     * </pre>
     *
     * @param args method arguments object
     */
    RFuture<Void> createGroupAsync(StreamCreateGroupArgs args);

    /**
     * Use createGroupAsync(StreamCreateGroupArgs) method instead
     */
    @Deprecated
    RFuture<Void> createGroupAsync(String groupName);

    /**
     * Use createGroupAsync(StreamCreateGroupArgs) method instead
     */
    @Deprecated
    RFuture<Void> createGroupAsync(String groupName, StreamMessageId id);

    /**
     * Removes group by name.
     * 
     * @param groupName - name of group
     * @return void
     */
    RFuture<Void> removeGroupAsync(String groupName);

    /**
     * Creates consumer of the group by name.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param groupName - name of group
     * @param consumerName - name of consumer
     */
    RFuture<Void> createConsumerAsync(String groupName, String consumerName);

    /**
     * Removes consumer of the group by name.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @return number of pending messages owned by consumer
     */
    RFuture<Long> removeConsumerAsync(String groupName, String consumerName);
    
    /**
     * Updates next message id delivered to consumers. 
     * 
     * @param groupName - name of group
     * @param id - Stream Message ID
     * @return void
     */
    RFuture<Void> updateGroupMessageIdAsync(String groupName, StreamMessageId id);
    
    /**
     * Marks pending messages by group name and stream <code>ids</code> as correctly processed.
     * 
     * @param groupName - name of group
     * @param ids - stream ids
     * @return marked messages amount
     */
    RFuture<Long> ackAsync(String groupName, StreamMessageId... ids);

    /**
     * Returns common info about pending messages by group name.
     * 
     * @param groupName - name of group
     * @return result object
     */
    RFuture<PendingResult> getPendingInfoAsync(String groupName);

    /**
     * Returns list of common info about pending messages by group name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @see #pendingRangeAsync
     * 
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param endId - end Stream Message ID
     * @param count - amount of messages
     * @return list
     */
    RFuture<List<PendingEntry>> listPendingAsync(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);
    
    /**
     * Returns list of common info about pending messages by group and consumer name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @see #pendingRangeAsync
     * 
     * @param consumerName - name of consumer
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param count - amount of messages
     * @return list
     */
    RFuture<List<PendingEntry>> listPendingAsync(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

    /**
     * Returns list of common info about pending messages by group name.
     * Limited by start Stream Message ID and end Stream Message ID and count.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     *
     * @see #pendingRangeAsync
     *
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param count - amount of messages
     * @return list
     */
    RFuture<List<PendingEntry>> listPendingAsync(String groupName, StreamMessageId startId, StreamMessageId endId, int count);

    /**
     * Returns list of common info about pending messages by group and consumer name.
     * Limited by start Stream Message ID and end Stream Message ID and count.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     *
     * @see #pendingRangeAsync
     *
     * @param consumerName - name of consumer
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param count - amount of messages
     * @return list
     */
    RFuture<List<PendingEntry>> listPendingAsync(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count);

    /**
     * Returns stream data of pending messages by group name.
     * Limited by start Stream Message ID and end Stream Message ID and count.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * 
     * @see #listPendingAsync
     * 
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param count - amount of messages
     * @return map
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, StreamMessageId startId, StreamMessageId endId, int count);
    
    /**
     * Returns stream data of pending messages by group and customer name.
     * Limited by start Stream Message ID and end Stream Message ID and count.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * 
     * @see #listPendingAsync
     * 
     * @param consumerName - name of consumer
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param count - amount of messages
     * @return map
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count);

    /**
     * Returns stream data of pending messages by group name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @see #listPendingAsync
     *
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param count - amount of messages
     * @return map
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

    /**
     * Returns stream data of pending messages by group and customer name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @see #listPendingAsync
     *
     * @param consumerName - name of consumer
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param count - amount of messages
     * @return map
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

    /**
     * Transfers ownership of pending messages by id to a new consumer
     * by name if idle time of messages and startId are greater than defined value.
     *
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param startId - start Stream Message ID
     * @return stream data mapped by Stream ID
     */
    RFuture<AutoClaimResult<K, V>> autoClaimAsync(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count);

    /**
     * Transfers ownership of pending messages by id to a new consumer
     * by name if idle time of messages and startId are greater than defined value.
     *
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param startId - start Stream Message ID
     * @return list of Stream Message IDs
     */
    RFuture<FastAutoClaimResult> fastAutoClaimAsync(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count);

    /**
     * Transfers ownership of pending messages by id to a new consumer 
     * by name if idle time of messages is greater than defined value. 
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param ids - Stream Message IDs
     * @return stream data mapped by Stream ID
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> claimAsync(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId... ids);
    
    /**
     * Transfers ownership of pending messages by id to a new consumer 
     * by name if idle time of messages is greater than defined value. 
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param ids - Stream Message IDs
     * @return list of Stream Message IDs
     */
    RFuture<List<StreamMessageId>> fastClaimAsync(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId... ids);

    /**
     * Read stream data from consumer group and multiple streams including current.
     *
     * @param args - method arguments object
     * @return stream data mapped by stream name and Stream Message ID
     */
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, StreamMultiReadGroupArgs args);

    /**
     * Read stream data from consumer group and current stream only.
     *
     * @param args - method arguments object
     * @return stream data mapped by Stream Message ID
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, StreamReadGroupArgs args);

    /**
     * * Use readGroup(String, String, StreamReadGroupArgs) method instead
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    @Deprecated
    RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, StreamMessageId... ids);
    
    /**
     * Use readGroup(String, String, StreamReadGroupArgs) method instead
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    @Deprecated
    RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, int count, StreamMessageId... ids);

    /**
     * Use readGroup(String, String, StreamReadGroupArgs) method instead
     *
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    @Deprecated
    RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId... ids);
    
    /**
     * Use readGroup(String, String, StreamReadGroupArgs) method instead
     *
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param ids - collection of Stream IDs
     * @return stream data mapped by Stream ID
     */
    @Deprecated
    RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId... ids);

    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param id - starting message id for this stream
     * @param nameToId - Stream Message ID mapped by stream name
     * @return stream data mapped by key and Stream Message ID
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, StreamMessageId id, Map<String, StreamMessageId> nameToId);
    
    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param id - starting message id for this stream
     * @param nameToId - Stream Message ID mapped by stream name
     * @return stream data mapped by key and Stream Message ID
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
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
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id, String key2, StreamMessageId id2);

    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
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
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2, String key3, StreamMessageId id3);
    
    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param timeout - time interval to wait for stream data availability
     * @param unit - time interval unit
     * @param id - starting message id for this stream
     * @param nameToId - Stream Message ID mapped by stream name
     * @return stream data mapped by key and Stream Message ID
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);
    
    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2 - starting message id for second stream
     * @return stream data mapped by key and Stream Message ID
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2);

    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
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
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3);

    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param count - stream data size limit
     * @param id - starting message id for this stream
     * @param key2 - name of second stream
     * @param id2  - starting message id for second stream
     * @return stream data mapped by key and Stream Message ID
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2);

    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
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
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2,
            String key3, StreamMessageId id3);

    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
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
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2);

    /**
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
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
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2, String key3, StreamMessageId id3);

    /**
     * Returns number of entries in stream
     * 
     * @return size of stream
     */
    RFuture<Long> sizeAsync();

    /**
     * Appends a new entry/entries and returns generated Stream Message ID
     *
     * @param args - method arguments object
     * @return Stream Message ID
     */
    RFuture<StreamMessageId> addAsync(StreamAddArgs<K, V> args);

    /**
     * Appends a new entry/entries by specified Stream Message ID
     *
     * @param id - Stream Message ID
     * @param args - method arguments object
     */
    RFuture<Void> addAsync(StreamMessageId id, StreamAddArgs<K, V> args);

    /*
     * Use addAsync(StreamAddArgs) method instead
     *
     */
    @Deprecated
    RFuture<StreamMessageId> addAsync(K key, V value);
    
    /*
     * Use addAsync(StreamMessageId, StreamAddArgs) method instead
     *
     */
    @Deprecated
    RFuture<Void> addAsync(StreamMessageId id, K key, V value);
    
    /*
     * Use addAsync(StreamAddArgs) method instead
     *
     */
    @Deprecated
    RFuture<StreamMessageId> addAsync(K key, V value, int trimLen, boolean trimStrict);

    /*
     * Use addAsync(StreamMessageId, StreamAddArgs) method instead
     *
     */
    @Deprecated
    RFuture<Void> addAsync(StreamMessageId id, K key, V value, int trimLen, boolean trimStrict);
    
    /*
     * Use addAsync(StreamAddArgs) method instead
     *
     */
    @Deprecated
    RFuture<StreamMessageId> addAllAsync(Map<K, V> entries);
    
    /*
     * Use addAsync(StreamMessageId, StreamAddArgs) method instead
     *
     */
    @Deprecated
    RFuture<Void> addAllAsync(StreamMessageId id, Map<K, V> entries);
    
    /*
     * Use addAsync(StreamAddArgs) method instead
     *
     */
    @Deprecated
    RFuture<StreamMessageId> addAllAsync(Map<K, V> entries, int trimLen, boolean trimStrict);

    /*
     * Use addAsync(StreamMessageId, StreamAddArgs) method instead
     *
     */
    @Deprecated
    RFuture<Void> addAllAsync(StreamMessageId id, Map<K, V> entries, int trimLen, boolean trimStrict);

    /**
     * Read stream data from multiple streams including current.
     *
     * @param args - method arguments object
     * @return stream data mapped by stream name and Stream Message ID
     */
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(StreamMultiReadArgs args);

    /**
     * Read stream data from current stream only.
     *
     * @param args - method arguments object
     * @return stream data mapped by Stream Message ID
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(StreamReadArgs args);

    /*
     * Use readAsync(StreamReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(StreamMessageId... ids);
    
    /*
     * Use readAsync(StreamReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(int count, StreamMessageId... ids);

    /*
     * Use readAsync(StreamReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(long timeout, TimeUnit unit, StreamMessageId... ids);
    
    /*
     * Use readAsync(StreamReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(int count, long timeout, TimeUnit unit, StreamMessageId... ids);

    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(StreamMessageId id, String name2, StreamMessageId id2);

    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, StreamMessageId id, String name2, StreamMessageId id2);

    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2);

    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2);

    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /*
     * Use readAsync(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);
    
    /**
     * Returns stream data in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> rangeAsync(StreamMessageId startId, StreamMessageId endId);

    /**
     * Returns stream data in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> rangeAsync(int count, StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in reverse order in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> rangeReversedAsync(StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in reverse order in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    RFuture<Map<StreamMessageId, Map<K, V>>> rangeReversedAsync(int count, StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Removes messages by id.
     * 
     * @param ids - id of messages to remove
     * @return deleted messages amount
     */
    RFuture<Long> removeAsync(StreamMessageId... ids);

    RFuture<Long> trimAsync(StreamTrimArgs args);

    RFuture<Long> trimNonStrictAsync(StreamTrimArgs args);

    /**
     * Trims stream using MAXLEN strategy to specified size
     * 
     * @param size - new size of stream
     * @return number of deleted messages
     */
    @Deprecated
    RFuture<Long> trimAsync(int size);

    /**
     * Trims stream to specified size
     *
     * @param strategy - trim strategy
     * @param threshold - new size of stream
     * @return number of deleted messages
     */
    @Deprecated
    RFuture<Long> trimAsync(TrimStrategy strategy, int threshold);

    /**
     * Trims stream using MAXLEN strategy to almost exact trimming threshold.
     * 
     * @param size - new size of stream
     * @return number of deleted messages
     */
    @Deprecated
    RFuture<Long> trimNonStrictAsync(int size);

    /**
     * Trims stream using almost exact trimming threshold.
     *
     * @param strategy - trim strategy
     * @param threshold - trim threshold
     * @return number of deleted messages
     */
    @Deprecated
    RFuture<Long> trimNonStrictAsync(TrimStrategy strategy, int threshold);

    /**
     * Trims stream using almost exact trimming threshold up to limit.
     *
     * @param strategy - trim strategy
     * @param threshold - trim threshold
     * @param limit - trim limit
     * @return number of deleted messages
     */
    @Deprecated
    RFuture<Long> trimNonStrictAsync(TrimStrategy strategy, int threshold, int limit);

    /**
     * Returns information about this stream.
     * 
     * @return info object
     */
    RFuture<StreamInfo<K, V>> getInfoAsync();
    
    /**
     * Returns list of objects with information about groups belonging to this stream.
     * 
     * @return list of info objects 
     */
    RFuture<List<StreamGroup>> listGroupsAsync();

    /**
     * Returns list of objects with information about group customers for specified <code>groupName</code>.
     * 
     * @param groupName - name of group
     * @return list of info objects
     */
    RFuture<List<StreamConsumer>> listConsumersAsync(String groupName);
    
}
