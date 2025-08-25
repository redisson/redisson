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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
    List<PendingEntry> listPending(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

    /**
     * Returns stream data of pending messages by group and customer name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     *
     * @param args - method arguments object
     * @return list
     */
    List<PendingEntry> listPending(StreamPendingRangeArgs args);

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

    /**
     * Returns stream data in range by specified start Stream Message ID (included) and end Stream Message ID (included).
     * 
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @return stream data mapped by Stream Message ID
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> range(StreamMessageId startId, StreamMessageId endId);

    /**
     * Returns stream data in range by specified start Stream Message ID (included) and end Stream Message ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @return stream data mapped by Stream Message ID
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> range(int count, StreamMessageId startId, StreamMessageId endId);

    /**
     * Returns stream data in range.
     *
     * @param args - method arguments object
     * @return stream data mapped by Stream ID
     */
    Map<StreamMessageId, Map<K, V>> range(StreamRangeArgs args);

    /**
     * Returns stream data in reverse order in range by specified start Stream Message ID (included) and end Stream Message ID (included).
     * 
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @return stream data mapped by Stream Message ID
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> rangeReversed(StreamMessageId startId, StreamMessageId endId);

    /**
     * Returns stream data in reverse order in range by specified start Stream Message ID (included) and end Stream Message ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @return stream data mapped by Stream Message ID
     */
    @Deprecated
    Map<StreamMessageId, Map<K, V>> rangeReversed(int count, StreamMessageId startId, StreamMessageId endId);

    /**
     * Returns stream data in reverse order in range.
     *
     * @param args - method arguments object
     * @return stream data mapped by Stream ID
     */
    Map<StreamMessageId, Map<K, V>> rangeReversed(StreamRangeArgs args);

    /**
     * Removes messages by id.
     * 
     * @param ids - id of messages to remove
     * @return deleted messages amount
     */
    long remove(StreamMessageId... ids);

    /**
     * Removes messages.
     * Requires <b>Redis 8.2.0 and higher.</b>
     *
     * @param args - method arguments object
     * @return List of messages deletion statuses
     */
    List<Integer> remove(StreamRemoveArgs args);

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

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.StreamAddListener
     * @see org.redisson.api.listener.StreamRemoveListener
     * @see org.redisson.api.listener.StreamCreateGroupListener
     * @see org.redisson.api.listener.StreamRemoveGroupListener
     * @see org.redisson.api.listener.StreamCreateConsumerListener
     * @see org.redisson.api.listener.StreamRemoveConsumerListener
     * @see org.redisson.api.listener.StreamTrimListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    int addListener(ObjectListener listener);


}
