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
 * Interface for Redis Stream object.
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
     * Creates consumer group.
     * <p>
     * Usage examples:
     * <pre>
     * StreamMessageId id = stream.createGroup(StreamCreateGroupArgs.name("test").id(id).makeStream());
     * </pre>
     *
     * @param args method arguments object
     */
    void createGroup(StreamCreateGroupArgs args);

    /**
     * Use createGroup(StreamCreateGroupArgs) method instead
     */
    @Deprecated
    void createGroup(String groupName);

    /**
     * Use createGroup(StreamCreateGroupArgs) method instead
     */
    @Deprecated
    void createGroup(String groupName, StreamMessageId id);
    
    /**
     * Removes group by name.
     * 
     * @param groupName - name of group
     */
    void removeGroup(String groupName);

    /**
     * Creates consumer of the group by name.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param groupName - name of group
     * @param consumerName - name of consumer
     */
    void createConsumer(String groupName, String consumerName);

    /**
     * Removes consumer of the group by name.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @return number of pending messages owned by consumer
     */
    long removeConsumer(String groupName, String consumerName);

    /**
     * Updates next message id delivered to consumers. 
     * 
     * @param groupName - name of group
     * @param id - Stream Message ID
     */
    void updateGroupMessageId(String groupName, StreamMessageId id);
    
    /**
     * Marks pending messages by group name and stream <code>ids</code> as correctly processed.
     * 
     * @param groupName - name of group
     * @param ids - Stream Message IDs
     * @return marked messages amount
     */
    long ack(String groupName, StreamMessageId... ids);

    /**
     * Returns common info about pending messages by group name.
     * 
     * @param groupName - name of group
     * @return result object
     */
    PendingResult getPendingInfo(String groupName);
    
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
    List<PendingEntry> listPending(String groupName, StreamMessageId startId, StreamMessageId endId, int count);

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
    List<PendingEntry> listPending(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count);

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
    List<PendingEntry> listPending(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

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
    List<PendingEntry> listPending(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

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
    Map<StreamMessageId, Map<K, V>> pendingRange(String groupName, StreamMessageId startId, StreamMessageId endId, int count);

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
    Map<StreamMessageId, Map<K, V>> pendingRange(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count);

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
    Map<StreamMessageId, Map<K, V>> pendingRange(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

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
    Map<StreamMessageId, Map<K, V>> pendingRange(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

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
    AutoClaimResult<K, V> autoClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count);

    /**
     * Transfers ownership of pending messages by id to a new consumer 
     * by name if idle time of messages is greater than defined value. 
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param ids - Stream Message IDs
     * @return stream data mapped by Stream Message ID
     */
    Map<StreamMessageId, Map<K, V>> claim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId... ids);

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
    List<StreamMessageId> fastClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId... ids);

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
    FastAutoClaimResult fastAutoClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count);

    /**
     * Read stream data from consumer group and multiple streams including current.
     * <p>
     * Usage examples:
     * <pre>
     * Map result = stream.read("group1", "consumer1",  StreamMultiReadGroupArgs.greaterThan(id, "stream2", id2));
     * </pre>
     * <pre>
     * Map result = stream.read("group1", "consumer1", StreamMultiReadGroupArgs.greaterThan(id, "stream2", id2)
     *                                                                          .count(100)
     *                                                                          .timeout(Duration.ofSeconds(5))));
     * </pre>
     *
     * @param args - method arguments object
     * @return stream data mapped by stream name and Stream Message ID
     */
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMultiReadGroupArgs args);

    /**
     * Read stream data from consumer group and current stream only.
     * <p>
     * Usage examples:
     * <pre>
     * Map result = stream.read("group1", "consumer1",  StreamReadGroupArgs.greaterThan(id));
     * </pre>
     * <pre>
     * Map result = stream.read("group1", "consumer1", StreamReadGroupArgs.greaterThan(id)
     *                                                                          .count(100)
     *                                                                          .timeout(Duration.ofSeconds(5))));
     * </pre>
     *
     * @param args - method arguments object
     * @return stream data mapped by Stream Message ID
     */
    Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, StreamReadGroupArgs args);

    /*
     * Use readGroup(String, String, StreamReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, StreamMessageId... ids);

    /*
     * Use readGroup(String, String, StreamReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, int count, StreamMessageId... ids);
    
    /*
     * Use readGroup(String, String, StreamReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId... ids);

    /*
     * Use readGroup(String, String, StreamReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId... ids);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, Map<String, StreamMessageId> nameToId);
    
    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2, String key3, StreamMessageId id3);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2);

    /*
     * Use readGroup(String, String, StreamMultiReadGroupArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2, String key3, StreamMessageId id3);
    
    /**
     * Returns number of entries in stream
     * 
     * @return size of stream
     */
    long size();

    /**
     * Appends a new entry/entries and returns generated Stream Message ID
     * <p>
     * Usage examples:
     * <pre>
     * StreamMessageId id = stream.add(StreamAddArgs.entry(15, 37));
     * </pre>
     * <pre>
     * StreamMessageId id = stream.add(StreamAddArgs.entries(15, 37, 23, 43)
     *                                 .trim(TrimStrategy.MAXLEN, 100)));
     * </pre>
     *
     * @param args - method arguments object
     * @return Stream Message ID
     */
    StreamMessageId add(StreamAddArgs<K, V> args);

    /**
     * Appends a new entry/entries by specified Stream Message ID
     * <p>
     * Usage examples:
     * <pre>
     * stream.add(id, StreamAddArgs.entry(15, 37));
     * </pre>
     * <pre>
     * stream.add(id, StreamAddArgs.entries(15, 37, 23, 43)
     *                                 .trim(TrimStrategy.MAXLEN, 100)));
     * </pre>
     *
     * @param id - Stream Message ID
     * @param args - method arguments object
     */
    void add(StreamMessageId id, StreamAddArgs<K, V> args);

    /*
     * Use add(StreamAddArgs) method instead
     *
     */
    @Deprecated
    StreamMessageId add(K key, V value);
    
    /*
     * Use add(StreamMessageId, StreamAddArgs) method instead
     *
     */
    @Deprecated
    void add(StreamMessageId id, K key, V value);
    
    /*
     * Use add(StreamAddArgs) method instead
     *
     */
    @Deprecated
    StreamMessageId add(K key, V value, int trimLen, boolean trimStrict);

    /*
     * Use add(StreamMessageId, StreamAddArgs) method instead
     *
     */
    @Deprecated
    void add(StreamMessageId id, K key, V value, int trimLen, boolean trimStrict);
    
    /*
     * Use add(StreamAddArgs) method instead
     *
     */
    @Deprecated
    StreamMessageId addAll(Map<K, V> entries);
    
    /*
     * Use add(StreamMessageId, StreamAddArgs) method instead
     *
     */
    @Deprecated
    void addAll(StreamMessageId id, Map<K, V> entries);
    
    /*
     * Use add(StreamAddArgs) method instead
     *
     */
    @Deprecated
    StreamMessageId addAll(Map<K, V> entries, int trimLen, boolean trimStrict);
    
    /*
     * Use add(StreamMessageId, StreamAddArgs) method instead
     *
     */
    @Deprecated
    void addAll(StreamMessageId id, Map<K, V> entries, int trimLen, boolean trimStrict);

    /**
     * Read stream data from multiple streams including current.
     * <p>
     * Usage examples:
     * <pre>
     * Map result = stream.read(StreamMultiReadArgs.greaterThan(id, "stream2", id2));
     * </pre>
     * <pre>
     * Map result = stream.read(StreamMultiReadArgs.greaterThan(id, "stream2", id2)
     *                                 .count(100)
     *                                 .timeout(Duration.ofSeconds(5))));
     * </pre>
     *
     * @param args - method arguments object
     * @return stream data mapped by stream name and Stream Message ID
     */
    Map<String, Map<StreamMessageId, Map<K, V>>> read(StreamMultiReadArgs args);

    /**
     * Read stream data from current stream only.
     * <p>
     * Usage examples:
     * <pre>
     * Map result = stream.read(StreamReadArgs.greaterThan(id));
     * </pre>
     * <pre>
     * Map result = stream.read(StreamReadArgs.greaterThan(id)
     *                                 .count(100)
     *                                 .timeout(Duration.ofSeconds(5))));
     * </pre>
     *
     * @param args - method arguments object
     * @return stream data mapped by Stream Message ID
     */
    Map<StreamMessageId, Map<K, V>> read(StreamReadArgs args);

    /*
     * Use read(StreamReadArgs) method instead
     *
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> read(StreamMessageId... ids);

    /*
     * Use read(StreamReadArgs) method instead
     *
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> read(int count, StreamMessageId... ids);
    
    /*
     * Use read(StreamReadArgs) method instead
     *
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> read(long timeout, TimeUnit unit, StreamMessageId... ids);

    /*
     * Use read(StreamReadArgs) method instead
     *
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> read(int count, long timeout, TimeUnit unit, StreamMessageId... ids);

    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(StreamMessageId id, String name2, StreamMessageId id2);

    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, StreamMessageId id, String name2, StreamMessageId id2);

    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /*
     * Use read(StreamReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2);

    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2);

    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, String name2, StreamMessageId id2, String name3, StreamMessageId id3);
    
    /*
     * Use read(StreamMultiReadArgs) method instead
     *
     */
    @Deprecated
    Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> nameToId);

    /**
     * Returns stream data in range by specified start Stream Message ID (included) and end Stream Message ID (included).
     * 
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @return stream data mapped by Stream Message ID
     */
    Map<StreamMessageId, Map<K, V>> range(StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in range by specified start Stream Message ID (included) and end Stream Message ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @return stream data mapped by Stream Message ID
     */
    Map<StreamMessageId, Map<K, V>> range(int count, StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in reverse order in range by specified start Stream Message ID (included) and end Stream Message ID (included).
     * 
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @return stream data mapped by Stream Message ID
     */
    Map<StreamMessageId, Map<K, V>> rangeReversed(StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in reverse order in range by specified start Stream Message ID (included) and end Stream Message ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @return stream data mapped by Stream Message ID
     */
    Map<StreamMessageId, Map<K, V>> rangeReversed(int count, StreamMessageId startId, StreamMessageId endId);

    /**
     * Removes messages by id.
     * 
     * @param ids - id of messages to remove
     * @return deleted messages amount
     */
    long remove(StreamMessageId... ids);

    /**
     * Trims stream using strict trimming.
     * <p>
     * Usage example:
     * <pre>
     * long result = stream.trim(StreamTrimArgs.maxLen(100).noLimit());
     * </pre>
     *
     * @param args - method arguments object
     * @return number of deleted messages
     */
    long trim(StreamTrimArgs args);

    /**
     * Trims stream using non-strict trimming.
     * <p>
     * Usage example:
     * <pre>
     * long result = stream.trimNonStrict(StreamTrimArgs.maxLen(100).noLimit());
     * </pre>
     *
     * @param args - method arguments object
     * @return number of deleted messages
     */
    long trimNonStrict(StreamTrimArgs args);

    /*
     * Use trim(StreamTrimArgs) method instead
     *
     */
    @Deprecated
    long trim(int size);

    /*
     * Use trim(StreamTrimArgs) method instead
     *
     */
    @Deprecated
    long trim(TrimStrategy strategy, int threshold);

    /*
     * Use trimNonStrict(StreamTrimArgs) method instead
     *
     */
    @Deprecated
    long trimNonStrict(int size);

    /*
     * Use trimNonStrict(StreamTrimArgs) method instead
     *
     */
    @Deprecated
    long trimNonStrict(TrimStrategy strategy, int threshold);

    /**
     * Trims stream using almost exact trimming threshold up to limit.
     *
     * @param strategy - trim strategy
     * @param threshold - trim threshold
     * @param limit - trim limit
     * @return number of deleted messages
     */
    @Deprecated
    long trimNonStrict(TrimStrategy strategy, int threshold, int limit);

    /**
     * Returns information about this stream.
     * 
     * @return info object
     */
    StreamInfo<K, V> getInfo();
    
    /**
     * Returns list of common info about groups belonging to this stream.
     * 
     * @return list of info objects 
     */
    List<StreamGroup> listGroups();

    /**
     * Returns list of common info about group customers for specified <code>groupName</code>.
     * 
     * @param groupName - name of group
     * @return list of info objects
     */
    List<StreamConsumer> listConsumers(String groupName);
    
}
