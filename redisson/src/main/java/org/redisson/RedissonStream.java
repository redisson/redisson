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
package org.redisson;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.redisson.api.*;
import org.redisson.api.listener.*;
import org.redisson.api.stream.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.StreamEntryStatus;
import org.redisson.client.protocol.decoder.*;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonStream<K, V> extends RedissonExpirable implements RStream<K, V> {

    public RedissonStream(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    public RedissonStream(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    protected void checkKey(Object key) {
        if (key == null) {
            throw new NullPointerException("key can't be null");
        }
    }

    protected void checkValue(Object value) {
        if (value == null) {
            throw new NullPointerException("value can't be null");
        }
    }

    @Override
    public void createGroup(StreamCreateGroupArgs args) {
        get(createGroupAsync(args));
    }

    @Override
    public RFuture<Void> createGroupAsync(StreamCreateGroupArgs args) {
        StreamCreateGroupParams pps = (StreamCreateGroupParams) args;
        List<Object> params = new LinkedList<>();
        params.add("CREATE");
        params.add(getRawName());
        params.add(pps.getName());
        params.add(pps.getId());
        if (pps.isMakeStream()) {
            params.add("MKSTREAM");
        }
        if (pps.getEntriesRead() > 0) {
            params.add("ENTRIESREAD");
            params.add(pps.getEntriesRead());
        }
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XGROUP, params.toArray());
    }

    @Override
    public RFuture<Long> ackAsync(String groupName, StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());
        params.add(groupName);
        params.addAll(Arrays.asList(ids));
        
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XACK, params.toArray());
    }

    @Override
    public long ack(String groupName, StreamMessageId... id) {
        return get(ackAsync(groupName, id));
    }

    @Override
    public RFuture<Map<StreamMessageId, StreamEntryStatus>> ackAsync(StreamAckArgs args) {
        StreamAckParams pps = (StreamAckParams) args;
        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());
        params.add(pps.getGroupName());

        if (pps.getRefPolicy() != null) {
            params.add(pps.getRefPolicy());
        }

        params.add("IDS");
        params.add(pps.getIds().length);

        List<StreamMessageId> ids = Arrays.asList(pps.getIds());
        params.addAll(ids);

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                new RedisCommand<>("XACKDEL", new StreamEntryStatusDecoder<>(ids)),
                params.toArray());
    }

    @Override
    public Map<StreamMessageId, StreamEntryStatus> ack(StreamAckArgs args) {
        return get(ackAsync(args));
    }

    @Override
    public RFuture<PendingResult> getPendingInfoAsync(String groupName) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XPENDING, getRawName(), groupName);
    }

    @Override
    public PendingResult getPendingInfo(String groupName) {
        return get(getPendingInfoAsync(groupName));
    }
    
    @Override
    public RFuture<List<PendingEntry>> listPendingAsync(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XPENDING_ENTRIES, getRawName(), groupName, startId, endId, count, consumerName);
    }

    @Override
    public RFuture<List<PendingEntry>> listPendingAsync(String groupName, StreamMessageId startId, StreamMessageId endId, int count) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XPENDING_ENTRIES, getRawName(), groupName, startId, endId, count);
    }

    @Override
    public RFuture<List<PendingEntry>> listPendingAsync(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XPENDING_ENTRIES, getRawName(), groupName, "IDLE", idleTimeUnit.toMillis(idleTime), startId, endId, count);
    }

    @Override
    public RFuture<List<PendingEntry>> listPendingAsync(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XPENDING_ENTRIES, getRawName(), groupName, "IDLE", idleTimeUnit.toMillis(idleTime), startId, endId, count, consumerName);
    }

    @Override
    public List<PendingEntry> listPending(String groupName, StreamMessageId startId, StreamMessageId endId, int count) {
        return get(listPendingAsync(groupName, startId, endId, count));
    }

    @Override
    public List<PendingEntry> listPending(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count) {
        return get(listPendingAsync(groupName, consumerName, startId, endId, count));
    }

    @Override
    public List<PendingEntry> listPending(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count) {
        return get(listPendingAsync(groupName, startId, endId, idleTime, idleTimeUnit, count));
    }

    @Override
    public List<PendingEntry> listPending(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count) {
        return get(listPendingAsync(groupName, consumerName, startId, endId, idleTime, idleTimeUnit, count));
    }

    @Override
    public List<PendingEntry> listPending(StreamPendingRangeArgs args) {
        return get(listPendingAsync(args));
    }

    @Override
    public RFuture<List<PendingEntry>> listPendingAsync(StreamPendingRangeArgs args) {
        StreamPendingRangeParams pps = (StreamPendingRangeParams) args;
        List<Object> params = new LinkedList<>();
        params.add(getRawName());
        params.add(pps.getGroupName());
        if (pps.getIdleTime() != null) {
            params.add("IDLE");
            params.add(pps.getIdleTime().toMillis());
        }
        params.add(value(pps.getStartId(), pps.isStartIdExclusive()));
        params.add(value(pps.getEndId(), pps.isEndIdExclusive()));
        params.add(pps.getCount());
        if (pps.getConsumerName() != null) {
            params.add(pps.getConsumerName());
        }
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XPENDING_ENTRIES, params.toArray());
    }

    @Override
    public List<StreamMessageId> fastClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit,
            StreamMessageId... ids) {
        return get(fastClaimAsync(groupName, consumerName, idleTime, idleTimeUnit, ids));
    }

    @Override
    public RFuture<List<StreamMessageId>> fastClaimAsync(String groupName, String consumerName, long idleTime,
            TimeUnit idleTimeUnit, StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());
        params.add(groupName);
        params.add(consumerName);
        params.add(idleTimeUnit.toMillis(idleTime));
        
        for (StreamMessageId id : ids) {
            params.add(id.toString());
        }
        
        params.add("JUSTID");
        
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XCLAIM_IDS, params.toArray());
    }

    @Override
    public AutoClaimResult<K, V> autoClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count) {
        return get(autoClaimAsync(groupName, consumerName, idleTime, idleTimeUnit, startId, count));
    }

    @Override
    public RFuture<AutoClaimResult<K, V>> autoClaimAsync(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count) {
        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        params.add(groupName);
        params.add(consumerName);
        params.add(idleTimeUnit.toMillis(idleTime));
        params.add(startId.toString());
        params.add("COUNT");
        params.add(count);

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XAUTOCLAIM, params.toArray());
    }

    @Override
    public FastAutoClaimResult fastAutoClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count) {
        return get(fastAutoClaimAsync(groupName, consumerName, idleTime, idleTimeUnit, startId, count));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMultiReadGroupArgs args) {
        return get(readGroupAsync(groupName, consumerName, args));
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, StreamReadGroupArgs args) {
        return get(readGroupAsync(groupName, consumerName, args));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, StreamMultiReadGroupArgs args) {
        StreamMultiReadGroupParams rp = (StreamMultiReadGroupParams) args;

        List<Object> params = new ArrayList<>();
        params.add("GROUP");
        params.add(groupName);
        params.add(consumerName);

        if (rp.getCount() > 0) {
            params.add("COUNT");
            params.add(rp.getCount());
        }

        if (rp.getTimeout() != null) {
            params.add("BLOCK");
            params.add(rp.getTimeout().toMillis());
        }

        if (rp.getMinIdleTime() != null) {
            params.add("CLAIM");
            params.add(rp.getMinIdleTime().toMillis());
        }

        if (rp.isNoAck()) {
            params.add("NOACK");
        }

        params.add("STREAMS");
        params.add(getRawName());
        params.addAll(rp.getOffsets().keySet());

        if (rp.getId1() == null) {
            params.add(">");
        } else {
            params.add(rp.getId1().toString());
        }

        for (StreamMessageId nextId : rp.getOffsets().values()) {
            params.add(nextId.toString());
        }

        if (rp.getTimeout() != null) {
            return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XREADGROUP_BLOCKING, params.toArray());
        }
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XREADGROUP, params.toArray());
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, StreamReadGroupArgs args) {
        StreamReadGroupParams rp = (StreamReadGroupParams) args;

        List<Object> params = new ArrayList<>();
        params.add("GROUP");
        params.add(groupName);
        params.add(consumerName);

        if (rp.getCount() > 0) {
            params.add("COUNT");
            params.add(rp.getCount());
        }

        if (rp.getTimeout() != null) {
            params.add("BLOCK");
            params.add(rp.getTimeout().toMillis());
        }

        if (rp.getMinIdleTime() != null) {
            params.add("CLAIM");
            params.add(rp.getMinIdleTime().toMillis());
        }

        if (rp.isNoAck()) {
            params.add("NOACK");
        }

        params.add("STREAMS");
        params.add(getRawName());

        if (rp.getId1() == null) {
            params.add(">");
        } else {
            params.add(rp.getId1().toString());
        }

        if (rp.getTimeout() != null) {
            return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XREADGROUP_BLOCKING_SINGLE, params.toArray());
        }
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XREADGROUP_SINGLE, params.toArray());
    }

    @Override
    public RFuture<FastAutoClaimResult> fastAutoClaimAsync(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit, StreamMessageId startId, int count) {
        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        params.add(groupName);
        params.add(consumerName);
        params.add(idleTimeUnit.toMillis(idleTime));
        params.add(startId.toString());
        params.add("COUNT");
        params.add(count);
        params.add("JUSTID");

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XAUTOCLAIM_IDS, params.toArray());
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> claimAsync(String groupName, String consumerName, long idleTime,
            TimeUnit idleTimeUnit, StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());
        params.add(groupName);
        params.add(consumerName);
        params.add(idleTimeUnit.toMillis(idleTime));
        
        for (StreamMessageId id : ids) {
            params.add(id.toString());
        }
        
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XCLAIM, params.toArray());
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> claim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit,
                                                    StreamMessageId... ids) {
        return get(claimAsync(groupName, consumerName, idleTime, idleTimeUnit, ids));
    }

    @Override
    public long size() {
        return get(sizeAsync());
    }
    
    @Override
    public RFuture<Long> sizeAsync() {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XLEN, getRawName());
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(StreamMultiReadArgs args) {
        return get(readAsync(args));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(StreamMultiReadArgs args) {
        StreamMultiReadParams rp = (StreamMultiReadParams) args;

        List<Object> params = new ArrayList<>();
        if (rp.getCount() > 0) {
            params.add("COUNT");
            params.add(rp.getCount());
        }

        if (rp.getTimeout() != null) {
            params.add("BLOCK");
            params.add(rp.getTimeout().toMillis());
        }

        params.add("STREAMS");
        params.add(getRawName());
        params.addAll(rp.getOffsets().keySet());

        params.add(rp.getId1());
        for (StreamMessageId nextId : rp.getOffsets().values()) {
            params.add(nextId.toString());
        }

        if (rp.getTimeout() != null) {
            return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREAD_BLOCKING, params.toArray());
        }
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREAD, params.toArray());
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> read(StreamReadArgs args) {
        return get(readAsync(args));
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(StreamReadArgs args) {
        StreamReadParams rp = (StreamReadParams) args;

        List<Object> params = new ArrayList<Object>();
        if (rp.getCount() > 0) {
            params.add("COUNT");
            params.add(rp.getCount());
        }

        if (rp.getTimeout() != null) {
            params.add("BLOCK");
            params.add(rp.getTimeout().toMillis());
        }

        params.add("STREAMS");
        params.add(getRawName());
        params.add(rp.getId1());

        if (rp.getTimeout() != null) {
            return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREAD_BLOCKING_SINGLE, params.toArray());
        }
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREAD_SINGLE, params.toArray());
    }

    @Override
    public StreamMessageId add(StreamAddArgs<K, V> args) {
        return get(addAsync(args));
    }

    @Override
    public void add(StreamMessageId id, StreamAddArgs<K, V> args) {
        get(addAsync(id, args));
    }

    @Override
    public RFuture<StreamMessageId> addAsync(StreamAddArgs<K, V> args) {
        return addCustomAsync(null, args);
    }

    @Override
    public RFuture<Void> addAsync(StreamMessageId id, StreamAddArgs<K, V> args) {
        return addCustomAsync(id, args);
    }

    public <R> RFuture<R> addCustomAsync(StreamMessageId id, StreamAddArgs<K, V> args) {
        StreamAddParams<K, V> pps = (StreamAddParams<K, V>) args;

        List<Object> params = new LinkedList<Object>();
        params.add(getRawName());

        if (pps.isNoMakeStream()) {
            params.add("NOMKSTREAM");
        }

        if (pps.getRefPolicy() != null) {
            params.add(pps.getRefPolicy());
        }

        if (pps.getProducerId() != null && pps.getIdempotentId() != null) {
            params.add("IDMP");
            params.add(pps.getProducerId());
            params.add(pps.getIdempotentId());
        } else if (pps.getProducerId() != null) {
            params.add("IDMPAUTO");
            params.add(pps.getProducerId());
        }

        if (pps.getMaxLen() > 0) {
            params.add("MAXLEN");
            if (!pps.isTrimStrict()) {
                params.add("~");
            }
            params.add(pps.getMaxLen());
        }
        if (pps.getMinId() != null) {
            params.add("MINID");
            if (!pps.isTrimStrict()) {
                params.add("~");
            }
            params.add(pps.getMinId());
        }

        if (pps.getLimit() > 0) {
            params.add("LIMIT");
            params.add(pps.getLimit());
        }

        if (id == null) {
            params.add("*");
        } else {
            params.add(id.toString());
        }

        for (java.util.Map.Entry<? extends K, ? extends V> t : pps.getEntries().entrySet()) {
            checkKey(t.getKey());
            checkValue(t.getValue());

            params.add(encodeMapKey(t.getKey()));
            params.add(encodeMapValue(t.getValue()));
        }

        if (id == null) {
            return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XADD, params.toArray());
        }
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XADD_VOID, params.toArray());
    }

    private <R> RFuture<R> addCustomAsync(StreamMessageId id, K key, V value, int trimLen, boolean trimStrict) {
        List<Object> params = new LinkedList<Object>();
        params.add(getRawName());
        
        if (trimLen > 0) {
            params.add("MAXLEN");
            if (!trimStrict) {
                params.add("~");
            }
            params.add(trimLen);
        }
        
        if (id == null) {
            params.add("*");
        } else {
            params.add(id.toString());
        }
        
        checkKey(key);
        checkValue(value);

        params.add(encodeMapKey(key));
        params.add(encodeMapValue(value));

        if (id == null) {
            return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XADD, params.toArray());
        }
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XADD_VOID, params.toArray());
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> rangeAsync(StreamRangeArgs args) {
        StreamRangeParams pps = (StreamRangeParams) args;
        List<Object> params = new LinkedList<Object>();
        params.add(getRawName());
        params.add(value(pps.getStartId(), pps.isStartIdExclusive()));
        params.add(value(pps.getEndId(), pps.isEndIdExclusive()));

        if (pps.getCount() > 0) {
            params.add("COUNT");
            params.add(pps.getCount());
        }

        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XRANGE, params.toArray());
    }

    private String value(StreamMessageId messageId, boolean exclusive) {
        if (exclusive && messageId != StreamMessageId.MAX && messageId != StreamMessageId.MIN) {
            StringBuilder element = new StringBuilder();
            element.append("(");
            element.append(messageId);
            return element.toString();
        } else {
            return messageId.toString();
        }
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> range(StreamRangeArgs args) {
        return get(rangeAsync(args));
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> rangeReversedAsync(StreamRangeArgs args) {
        StreamRangeParams pps = (StreamRangeParams) args;
        List<Object> params = new LinkedList<Object>();
        params.add(getRawName());
        params.add(value(pps.getStartId(), pps.isStartIdExclusive()));
        params.add(value(pps.getEndId(), pps.isEndIdExclusive()));

        if (pps.getCount() > 0) {
            params.add("COUNT");
            params.add(pps.getCount());
        }

        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREVRANGE, params.toArray());
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> rangeReversed(StreamRangeArgs args) {
        return get(rangeReversedAsync(args));
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> rangeAsync(int count, StreamMessageId startId, StreamMessageId endId) {
        List<Object> params = new LinkedList<Object>();
        params.add(getRawName());
        params.add(startId);
        params.add(endId);
        
        if (count > 0) {
            params.add("COUNT");
            params.add(count);
        }
        
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XRANGE, params.toArray());
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> range(int count, StreamMessageId startId, StreamMessageId endId) {
        return get(rangeAsync(count, startId, endId));
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> rangeReversedAsync(int count, StreamMessageId startId, StreamMessageId endId) {
        List<Object> params = new LinkedList<Object>();
        params.add(getRawName());
        params.add(startId);
        params.add(endId);
        
        if (count > 0) {
            params.add("COUNT");
            params.add(count);
        }
        
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREVRANGE, params.toArray());
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> rangeReversed(int count, StreamMessageId startId, StreamMessageId endId) {
        return get(rangeReversedAsync(count, startId, endId));
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> rangeAsync(StreamMessageId startId, StreamMessageId endId) {
        return rangeAsync(0, startId, endId);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> rangeReversedAsync(StreamMessageId startId, StreamMessageId endId) {
        return rangeReversedAsync(0, startId, endId);
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> range(StreamMessageId startId, StreamMessageId endId) {
        return range(0, startId, endId);
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> rangeReversed(StreamMessageId startId, StreamMessageId endId) {
        return rangeReversed(0, startId, endId);
    }

    @Override
    public RFuture<Long> removeAsync(StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());
        params.addAll(Arrays.asList(ids));

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XDEL, params.toArray());
    }

    @Override
    public long remove(StreamMessageId... ids) {
        return get(removeAsync(ids));
    }

    @Override
    public Map<StreamMessageId, StreamEntryStatus> remove(StreamRemoveArgs args) {
        return get(removeAsync(args));
    }

    @Override
    public RFuture<Map<StreamMessageId, StreamEntryStatus>> removeAsync(StreamRemoveArgs args) {
        StreamRemoveParams pps = (StreamRemoveParams) args;
        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());

        if (pps.getRefPolicy() != null) {
            params.add(pps.getRefPolicy());
        }

        params.add("IDS");
        params.add(pps.getIds().length);
        List<StreamMessageId> ids = Arrays.asList(pps.getIds());
        params.addAll(ids);

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                new RedisCommand<>("XDELEX", new StreamEntryStatusDecoder<>(ids)),
                params.toArray());
    }

    @Override
    public RFuture<Void> removeGroupAsync(String groupName) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XGROUP, "DESTROY", getRawName(), groupName);
    }

    @Override
    public void removeGroup(String groupName) {
        get(removeGroupAsync(groupName));
    }

    @Override
    public void createConsumer(String groupName, String consumerName) {
        get(createConsumerAsync(groupName, consumerName));
    }

    @Override
    public RFuture<Void> createConsumerAsync(String groupName, String consumerName) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XGROUP, "CREATECONSUMER", getRawName(), groupName, consumerName);
    }

    @Override
    public RFuture<Long> removeConsumerAsync(String groupName, String consumerName) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XGROUP_LONG, "DELCONSUMER", getRawName(), groupName, consumerName);
    }

    @Override
    public long removeConsumer(String groupName, String consumerName) {
        return get(removeConsumerAsync(groupName, consumerName));
    }

    @Override
    public RFuture<Void> updateGroupMessageIdAsync(String groupName, StreamMessageId id) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XGROUP, "SETID", getRawName(), groupName, id);
    }

    @Override
    public void updateGroupMessageId(String groupName, StreamMessageId id) {
        get(updateGroupMessageIdAsync(groupName, id));
    }
    
    @Override
    public StreamInfo<K, V> getInfo() {
        return get(getInfoAsync());
    }

    @Override
    public RFuture<StreamInfo<K, V>> getInfoAsync() {
        RedisCommand<StreamInfo<Object, Object>> xinfoStream = new RedisCommand<>("XINFO", "STREAM",
                                                                        new ListMultiDecoder2(
                                                                                new StreamInfoDecoder(),
                                                                                new CodecDecoder(),
                                                                                new ObjectMapReplayDecoder(codec)));
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, xinfoStream, getRawName());
    }

    @Override
    public List<StreamGroup> listGroups() {
        return get(listGroupsAsync());
    }

    @Override
    public RFuture<List<StreamGroup>> listGroupsAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XINFO_GROUPS, getRawName());
    }

    @Override
    public List<StreamConsumer> listConsumers(String groupName) {
        return get(listConsumersAsync(groupName));
    }

    @Override
    public RFuture<List<StreamConsumer>> listConsumersAsync(String groupName) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XINFO_CONSUMERS, getRawName(), groupName);
    }

    private static final RedisCommand<Map<StreamMessageId, Map<Object, Object>>> EVAL_XRANGE = new RedisCommand("EVAL", RedisCommands.XRANGE.getReplayMultiDecoder());
    
    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, StreamMessageId startId,
            StreamMessageId endId, int count) {
        return commandExecutor.evalReadAsync(getRawName(), codec, EVAL_XRANGE,
                "local pendingData = redis.call('xpending', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4]);" +
                "local result = {}; " +
                "for i = 1, #pendingData, 1 do " +
                    "local value = redis.call('xrange', KEYS[1], pendingData[i][1], pendingData[i][1]);" +
                    "table.insert(result, value[1]);" + 
                "end; " +
                "return result;",
                Collections.<Object>singletonList(getRawName()),
                groupName, startId, endId, count);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, String consumerName,
                                                                        StreamMessageId startId, StreamMessageId endId, int count) {
        return commandExecutor.evalReadAsync(getRawName(), codec, EVAL_XRANGE,
                "local pendingData = redis.call('xpending', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5]);" +
                "local result = {}; " +
                "for i = 1, #pendingData, 1 do " +
                    "local value = redis.call('xrange', KEYS[1], pendingData[i][1], pendingData[i][1]);" +
                    "table.insert(result, value[1]);" + 
                "end; " +
                "return result;",
                Collections.singletonList(getRawName()),
                groupName, startId, endId, count, consumerName);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, StreamMessageId startId,
                                                                      StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count) {
        return commandExecutor.evalReadAsync(getRawName(), codec, EVAL_XRANGE,
                "local pendingData = redis.call('xpending', KEYS[1], ARGV[1], 'IDLE', ARGV[2], ARGV[3], ARGV[4], ARGV[5]);" +
                "local result = {}; " +
                "for i = 1, #pendingData, 1 do " +
                    "local value = redis.call('xrange', KEYS[1], pendingData[i][1], pendingData[i][1]);" +
                    "table.insert(result, value[1]);" +
                "end; " +
                "return result;",
                Collections.singletonList(getRawName()),
                groupName, idleTimeUnit.toMillis(idleTime), startId, endId, count);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, String consumerName,
                                                                      StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count) {
        return commandExecutor.evalReadAsync(getRawName(), codec, EVAL_XRANGE,
                "local pendingData = redis.call('xpending', KEYS[1], ARGV[1], 'IDLE', ARGV[2], ARGV[3], ARGV[4], ARGV[5], ARGV[6]);" +
                "local result = {}; " +
                "for i = 1, #pendingData, 1 do " +
                    "local value = redis.call('xrange', KEYS[1], pendingData[i][1], pendingData[i][1]);" +
                    "table.insert(result, value[1]);" +
                "end; " +
                "return result;",
                Collections.singletonList(getRawName()),
                groupName, idleTimeUnit.toMillis(idleTime), startId, endId, count, consumerName);
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> pendingRange(String groupName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count) {
        return get(pendingRangeAsync(groupName, startId, endId, idleTime, idleTimeUnit, count));
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> pendingRange(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, long idleTime, TimeUnit idleTimeUnit, int count) {
        return get(pendingRangeAsync(groupName, consumerName, startId, endId, idleTime, idleTimeUnit, count));
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> pendingRange(String groupName, String consumerName, StreamMessageId startId,
                                                        StreamMessageId endId, int count) {
        return get(pendingRangeAsync(groupName, consumerName, startId, endId, count));
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> pendingRange(String groupName, StreamMessageId startId, StreamMessageId endId,
                                                        int count) {
        return get(pendingRangeAsync(groupName, startId, endId, count));
    }

    @Override
    public long trim(StreamTrimArgs args) {
        return get(trimAsync(args));
    }

    @Override
    public RFuture<Long> trimAsync(StreamTrimArgs args) {
        return trimAsync(args, true);
    }

    private RFuture<Long> trimAsync(StreamTrimArgs args, boolean trimStrict) {
        StreamTrimParams pps = (StreamTrimParams) args;

        List<Object> params = new LinkedList<>();
        params.add(getRawName());

        if (pps.getMaxLen() != null) {
            params.add("MAXLEN");
            if (!trimStrict) {
                params.add("~");
            }
            params.add(pps.getMaxLen());
        }
        if (pps.getMinId() != null) {
            params.add("MINID");
            if (!trimStrict) {
                params.add("~");
            }
            params.add(pps.getMinId());
        }

        if (pps.getLimit() > 0) {
            params.add("LIMIT");
            params.add(pps.getLimit());
        }

        if (pps.getRefPolicy() != null) {
            params.add(pps.getRefPolicy());
        }

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XTRIM, params.toArray());
    }

    @Override
    public long trimNonStrict(StreamTrimArgs args) {
        return get(trimNonStrictAsync(args));
    }

    @Override
    public RFuture<Long> trimNonStrictAsync(StreamTrimArgs args) {
        return trimAsync(args, false);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof StreamAddListener) {
            return addListenerAsync("__keyevent@*:xadd", (StreamAddListener) listener, StreamAddListener::onAdd);
        }
        if (listener instanceof StreamRemoveListener) {
            return addListenerAsync("__keyevent@*:xdel", (StreamRemoveListener) listener, StreamRemoveListener::onRemove);
        }
        if (listener instanceof StreamCreateConsumerListener) {
            return addListenerAsync("__keyevent@*:xgroup-createconsumer", (StreamCreateConsumerListener) listener, StreamCreateConsumerListener::onCreateConsumer);
        }
        if (listener instanceof StreamRemoveConsumerListener) {
            return addListenerAsync("__keyevent@*:xgroup-delconsumer", (StreamRemoveConsumerListener) listener, StreamRemoveConsumerListener::onRemoveConsumer);
        }
        if (listener instanceof StreamCreateGroupListener) {
            return addListenerAsync("__keyevent@*:xgroup-create", (StreamCreateGroupListener) listener, StreamCreateGroupListener::onCreateGroup);
        }
        if (listener instanceof StreamRemoveGroupListener) {
            return addListenerAsync("__keyevent@*:xgroup-destroy", (StreamRemoveGroupListener) listener, StreamRemoveGroupListener::onRemoveGroup);
        }
        if (listener instanceof TrackingListener) {
            return addTrackingListenerAsync((TrackingListener) listener);
        }

        return super.addListenerAsync(listener);
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof StreamAddListener) {
            return addListener("__keyevent@*:xadd", (StreamAddListener) listener, StreamAddListener::onAdd);
        }
        if (listener instanceof StreamRemoveListener) {
            return addListener("__keyevent@*:xdel", (StreamRemoveListener) listener, StreamRemoveListener::onRemove);
        }
        if (listener instanceof StreamCreateConsumerListener) {
            return addListener("__keyevent@*:xgroup-createconsumer", (StreamCreateConsumerListener) listener, StreamCreateConsumerListener::onCreateConsumer);
        }
        if (listener instanceof StreamRemoveConsumerListener) {
            return addListener("__keyevent@*:xgroup-delconsumer", (StreamRemoveConsumerListener) listener, StreamRemoveConsumerListener::onRemoveConsumer);
        }
        if (listener instanceof StreamCreateGroupListener) {
            return addListener("__keyevent@*:xgroup-create", (StreamCreateGroupListener) listener, StreamCreateGroupListener::onCreateGroup);
        }
        if (listener instanceof StreamRemoveGroupListener) {
            return addListener("__keyevent@*:xgroup-destroy", (StreamRemoveGroupListener) listener, StreamRemoveGroupListener::onRemoveGroup);
        }
        if (listener instanceof StreamTrimListener) {
            return addListener("__keyevent@*:xtrim", (StreamTrimListener) listener, StreamTrimListener::onTrim);
        }
        if (listener instanceof TrackingListener) {
            return addTrackingListener((TrackingListener) listener);
        }

        return super.addListener(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        removeTrackingListener(listenerId);
        removeListener(listenerId, "__keyevent@*:xadd", "__keyevent@*:xdel", "__keyevent@*:xgroup-createconsumer",
                            "__keyevent@*:xgroup-delconsumer", "__keyevent@*:xgroup-create", "__keyevent@*:xgroup-destroy", "__keyevent@*:xtrim");
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        RFuture<Void> f1 = removeTrackingListenerAsync(listenerId);
        RFuture<Void> f2 = removeListenerAsync(listenerId,
                "__keyevent@*:xadd", "__keyevent@*:xdel", "__keyevent@*:xgroup-createconsumer",
                "__keyevent@*:xgroup-delconsumer", "__keyevent@*:xgroup-create", "__keyevent@*:xgroup-destroy", "__keyevent@*:xtrim");
        return new CompletableFutureWrapper<>(CompletableFuture.allOf(f1.toCompletableFuture(), f2.toCompletableFuture()));
    }


}