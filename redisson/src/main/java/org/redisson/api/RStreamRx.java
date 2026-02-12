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
package org.redisson.api;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.redisson.api.stream.*;
import org.redisson.client.protocol.StreamEntryStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
public interface RStreamRx<K, V> extends RExpirableRx {

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
    Completable createGroup(StreamCreateGroupArgs args);

    /**
     * Removes group by name.
     * 
     * @param groupName - name of group
     * @return void
     */
    Completable removeGroup(String groupName);

    /**
     * Creates consumer of the group by name.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param groupName - name of group
     * @param consumerName - name of consumer
     */
    Completable createConsumer(String groupName, String consumerName);

    /**
     * Removes consumer of the group by name.
     * 
     * @param groupName - name of group
     * @param consumerName - name of consumer
     * @return number of pending messages owned by consumer
     */
    Single<Long> removeConsumer(String groupName, String consumerName);
    
    /**
     * Updates next message id delivered to consumers. 
     * 
     * @param groupName - name of group
     * @param id - Stream Message ID
     * @return void
     */
    Completable updateGroupMessageId(String groupName, StreamMessageId id);
    
    /**
     * Marks pending messages by group name and stream <code>ids</code> as correctly processed.
     * 
     * @param groupName - name of group
     * @param ids - stream ids
     * @return marked messages amount
     */
    Single<Long> ack(String groupName, StreamMessageId... ids);

    /**
     * Acknowledges and conditionally deletes one or multiple entries (messages)
     * for a stream consumer group at the specified key.
     *
     * Requires <b>Redis 8.2.0 and higher.</b>
     *
     * @param args - method arguments object
     * @return map with entry statuses mapped by id
     */
    Single<Map<StreamMessageId, StreamEntryStatus>> ack(StreamAckArgs args);

    /**
     * Returns common info about pending messages by group name.
     * 
     * @param groupName - name of group
     * @return result object
     */
    Single<PendingResult> getPendingInfo(String groupName);

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
    @Deprecated
    Single<List<PendingEntry>> listPending(String groupName, StreamMessageId startId, StreamMessageId endId, int count);
    
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
    @Deprecated
    Single<List<PendingEntry>> listPending(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count);

    /**
     * Returns list of common info about pending messages by group name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @see #pendingRange
     *
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param endId - end Stream Message ID
     * @param count - amount of messages
     * @return list
     */
    @Deprecated
    Single<List<PendingEntry>> listPending(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

    /**
     * Returns list of common info about pending messages by group and consumer name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @see #pendingRange
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
    @Deprecated
    Single<List<PendingEntry>> listPending(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

    /**
     * Returns list of common info about pending messages by group and consumer name.
     * Limited by start Stream Message ID and end Stream Message ID and count.
     *
     * @param args - method arguments object
     * @return list
     */
    Single<List<PendingEntry>> listPending(StreamPendingRangeArgs args);

    /**
     * Returns stream data of pending messages by group name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @see #listPending
     *
     * @param groupName - name of group
     * @param startId - start Stream Message ID
     * @param endId - end Stream Message ID
     * @param idleTime - minimum idle time of messages
     * @param idleTimeUnit - idle time unit
     * @param count - amount of messages
     * @return map
     */
    Single<Map<StreamMessageId, Map<K, V>>> pendingRange(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

    /**
     * Returns stream data of pending messages by group and customer name.
     * Limited by minimum idle time, messages count, start and end Stream Message IDs.
     * <p>
     * {@link StreamMessageId#MAX} is used as max Stream Message ID
     * {@link StreamMessageId#MIN} is used as min Stream Message ID
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @see #listPending
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
    Single<Map<StreamMessageId, Map<K, V>>> pendingRange(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count);

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
    Single<Map<StreamMessageId, Map<K, V>>> claim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId... ids);

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
    Single<AutoClaimResult<K, V>> autoClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count);

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
    Single<FastAutoClaimResult> fastAutoClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count);

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
    Single<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroup(String groupName, String consumerName, StreamMultiReadGroupArgs args);

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
    Single<Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamReadGroupArgs args);

    /**
     * Returns number of entries in stream
     * 
     * @return size of stream
     */
    Single<Long> size();

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
    Single<StreamMessageId> add(StreamAddArgs<K, V> args);

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
    Completable add(StreamMessageId id, StreamAddArgs<K, V> args);

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
    Single<Map<String, Map<StreamMessageId, Map<K, V>>>> read(StreamMultiReadArgs args);

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
    Single<Map<StreamMessageId, Map<K, V>>> read(StreamReadArgs args);

    /**
     * Returns stream data in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    @Deprecated
    Single<Map<StreamMessageId, Map<K, V>>> range(StreamMessageId startId, StreamMessageId endId);

    /**
     * Returns stream data in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    @Deprecated
    Single<Map<StreamMessageId, Map<K, V>>> range(int count, StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in reverse order in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    @Deprecated
    Single<Map<StreamMessageId, Map<K, V>>> rangeReversed(StreamMessageId startId, StreamMessageId endId);
    
    /**
     * Returns stream data in reverse order in range by specified start Stream ID (included) and end Stream ID (included).
     * 
     * @param count - stream data size limit
     * @param startId - start Stream ID
     * @param endId - end Stream ID
     * @return stream data mapped by Stream ID
     */
    @Deprecated
    Single<Map<StreamMessageId, Map<K, V>>> rangeReversed(int count, StreamMessageId startId, StreamMessageId endId);

    /**
     * Returns stream data in range.
     *
     * @param args - method arguments object
     * @return stream data mapped by Stream ID
     */
    Single<Map<StreamMessageId, Map<K, V>>> range(StreamRangeArgs args);

    /**
     * Returns stream data in reverse order in range.
     *
     * @param args - method arguments object
     * @return stream data mapped by Stream ID
     */
    Single<Map<StreamMessageId, Map<K, V>>> rangeReversed(StreamRangeArgs args);

    /**
     * Removes messages by id.
     * 
     * @param ids - id of messages to remove
     * @return deleted messages amount
     */
    Single<Long> remove(StreamMessageId... ids);

    /**
     * Removes messages.
     * Requires <b>Redis 8.2.0 and higher.</b>
     *
     * @param args - method arguments object
     * @return map with entry statuses mapped by id
     */
    Single<Map<StreamMessageId, StreamEntryStatus>> remove(StreamRemoveArgs args);

    /**
     * Trims stream using strict trimming.
     *
     * @param args - method arguments object
     * @return number of deleted messages
     */
    Single<Long> trim(StreamTrimArgs args);

    /**
     * Trims stream using non-strict trimming.
     *
     * @param args - method arguments object
     * @return number of deleted messages
     */
    Single<Long> trimNonStrict(StreamTrimArgs args);

    /**
     * Returns information about this stream.
     * 
     * @return info object
     */
    Single<StreamInfo<K, V>> getInfo();
    
    /**
     * Returns list of objects with information about groups belonging to this stream.
     * 
     * @return list of info objects 
     */
    Single<List<StreamGroup>> listGroups();

    /**
     * Returns list of objects with information about group customers for specified <code>groupName</code>.
     * 
     * @param groupName - name of group
     * @return list of info objects
     */
    Single<List<StreamConsumer>> listConsumers(String groupName);
    
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
    Single<Map<StreamMessageId, Map<K, V>>> pendingRange(String groupName, StreamMessageId startId, StreamMessageId endId, int count);
    
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
    Single<Map<StreamMessageId, Map<K, V>>> pendingRange(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count);

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
     * @param listener object event listener
     * @return listener id
     */
    Single<Integer> addListener(ObjectListener listener);

}
