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
package org.redisson;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.redisson.api.*;
import org.redisson.api.stream.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.*;
import org.redisson.command.CommandAsyncExecutor;

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
    public void createGroup(String groupName) {
        get(createGroupAsync(groupName));
    }
    
    @Override
    public RFuture<Void> createGroupAsync(String groupName) {
        return createGroupAsync(groupName, StreamMessageId.NEWEST);
    }
    
    @Override
    public void createGroup(String groupName, StreamMessageId id) {
        get(createGroupAsync(groupName, id));
    }
    
    @Override
    public RFuture<Void> createGroupAsync(String groupName, StreamMessageId id) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XGROUP, "CREATE", getRawName(), groupName, id, "MKSTREAM");
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
    public RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, StreamMessageId... ids) {
        return readGroupAsync(groupName, consumerName, 0, ids);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, int count, StreamMessageId... ids) {
        return readGroupAsync(groupName, consumerName, count, 0, null, ids);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, long timeout, TimeUnit unit,
            StreamMessageId... ids) {
        return readGroupAsync(groupName, consumerName, 0, timeout, unit, ids);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> readGroupAsync(String groupName, String consumerName, int count, long timeout, TimeUnit unit,
            StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        params.add("GROUP");
        params.add(groupName);
        params.add(consumerName);
        
        if (count > 0) {
            params.add("COUNT");
            params.add(count);
        }
        
        if (timeout > 0) {
            params.add("BLOCK");
            params.add(unit.toMillis(timeout));
        }
        
        params.add("STREAMS");
        params.add(getRawName());

        if (ids.length == 0) {
            params.add(">");
        }
        
        for (StreamMessageId id : ids) {
            params.add(id.toString());
        }

        if (timeout > 0) {
            return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XREADGROUP_BLOCKING_SINGLE, params.toArray());
        }
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XREADGROUP_SINGLE, params.toArray());
    }
    
    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return readGroup(groupName, consumerName, 0, id, keyToId);
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return readGroupAsync(groupName, consumerName, 0, id, keyToId);
    }
    
    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return get(readGroupAsync(groupName, consumerName, count, id, keyToId));
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return readGroupAsync(groupName, consumerName, count, -1, null, id, keyToId);
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2) {
        return readGroupAsync(groupName, consumerName, count, timeout, unit, id, Collections.singletonMap(key2, id2));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2, String key3, StreamMessageId id3) {
        Map<String, StreamMessageId> params = new HashMap<String, StreamMessageId>(2);
        params.put(key2, id2);
        params.put(key3, id3);
        return readGroupAsync(groupName, consumerName, count, timeout, unit, id, params);
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return readGroupAsync(groupName, consumerName, 0, timeout, unit, id, keyToId);
    }
    
    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return get(readGroupAsync(groupName, consumerName, count, timeout, unit, id, keyToId));
    }
    
    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return readGroup(groupName, consumerName, 0, timeout, unit, id, keyToId);
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2) {
        return readGroupAsync(groupName, consumerName, id, Collections.singletonMap(key2, id2));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3) {
        Map<String, StreamMessageId> params = new HashMap<String, StreamMessageId>(2);
        params.put(key2, id2);
        params.put(key3, id3);
        return readGroupAsync(groupName, consumerName, id, params);
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2) {
        return readGroupAsync(groupName, consumerName, count, id, Collections.singletonMap(key2, id2));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2,
            String key3, StreamMessageId id3) {
        Map<String, StreamMessageId> params = new HashMap<String, StreamMessageId>(2);
        params.put(key2, id2);
        params.put(key3, id3);
        return readGroupAsync(groupName, consumerName, count, id, params);
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2) {
        return readGroupAsync(groupName, consumerName, timeout, unit, id, Collections.singletonMap(key2, id2));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2, String key3, StreamMessageId id3) {
        Map<String, StreamMessageId> params = new HashMap<String, StreamMessageId>(2);
        params.put(key2, id2);
        params.put(key3, id3);
        return readGroupAsync(groupName, consumerName, timeout, unit, id, params);
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2) {
        return get(readGroupAsync(groupName, consumerName, id, key2, id2));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3) {
        return get(readGroupAsync(groupName, consumerName, id, key2, id2, key3, id3));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2) {
        return get(readGroupAsync(groupName, consumerName, count, id, key2, id2));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3) {
        return get(readGroupAsync(groupName, consumerName, count, id, key2, id2, key3, id3));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2) {
        return get(readGroupAsync(groupName, consumerName, timeout, unit, id, key2, id2));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2, String key3, StreamMessageId id3) {
        return get(readGroupAsync(groupName, consumerName, timeout, unit, id, key2, id2, key3, id3));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2) {
        return get(readGroupAsync(groupName, consumerName, count, timeout, unit, id, key2, id2));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2, String key3, StreamMessageId id3) {
        return get(readGroupAsync(groupName, consumerName, count, timeout, unit, id, key2, id2, key3, id3));
    }
    
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readGroupAsync(String groupName, String consumerName, int count, long timeout, TimeUnit unit,
            StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        List<Object> params = new ArrayList<Object>();
        params.add("GROUP");
        params.add(groupName);
        params.add(consumerName);
        
        if (count > 0) {
            params.add("COUNT");
            params.add(count);
        }
        
        if (timeout > 0) {
            params.add("BLOCK");
            params.add(unit.toMillis(timeout));
        }
        
        params.add("STREAMS");
        params.add(getRawName());
        params.addAll(keyToId.keySet());

        if (id == null) {
            params.add(">");
        } else {
            params.add(id);
        }
        
        for (StreamMessageId nextId : keyToId.values()) {
            params.add(nextId.toString());
        }

        if (timeout > 0) {
            return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XREADGROUP_BLOCKING, params.toArray());
        }
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.XREADGROUP, params.toArray());
    }


    @Override
    public Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, StreamMessageId... ids) {
        return get(readGroupAsync(groupName, consumerName, ids));
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, int count, StreamMessageId... ids) {
        return get(readGroupAsync(groupName, consumerName, count, ids));
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, long timeout, TimeUnit unit, StreamMessageId... ids) {
        return get(readGroupAsync(groupName, consumerName, timeout, unit, ids));
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> readGroup(String groupName, String consumerName, int count, long timeout, TimeUnit unit,
            StreamMessageId... ids) {
        return get(readGroupAsync(groupName, consumerName, count, timeout, unit, ids));
    }

    @Override
    public StreamMessageId addAll(Map<K, V> entries) {
        return addAll(entries, 0, false);
    }
    
    @Override
    public RFuture<StreamMessageId> addAllAsync(Map<K, V> entries) {
        return addAllAsync(entries, 0, false);
    }
    
    @Override
    public void addAll(StreamMessageId id, Map<K, V> entries) {
        addAll(id, entries, 0, false);
    }

    @Override
    public RFuture<Void> addAllAsync(StreamMessageId id, Map<K, V> entries) {
        return addAllAsync(id, entries, 0, false);
    }

    @Override
    public StreamMessageId addAll(Map<K, V> entries, int trimLen, boolean trimStrict) {
        return get(addAllAsync(entries, trimLen, trimStrict));
    }
    
    @Override
    public RFuture<StreamMessageId> addAllAsync(Map<K, V> entries, int trimLen, boolean trimStrict) {
        return addAllCustomAsync(null, entries, trimLen, trimStrict);
    }
    
    @Override
    public void addAll(StreamMessageId id, Map<K, V> entries, int trimLen, boolean trimStrict) {
        get(addAllAsync(id, entries, trimLen, trimStrict));
    }

    private <R> RFuture<R> addAllCustomAsync(StreamMessageId id, Map<K, V> entries, int trimLen, boolean trimStrict) {
        List<Object> params = new ArrayList<Object>(entries.size()*2 + 1);
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
        
        for (java.util.Map.Entry<? extends K, ? extends V> t : entries.entrySet()) {
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
    
    @Override
    public RFuture<Void> addAllAsync(StreamMessageId id, Map<K, V> entries, int trimLen, boolean trimStrict) {
        return addAllCustomAsync(id, entries, trimLen, trimStrict);
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
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return read(0, id, keyToId);
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return readAsync(0, id, keyToId);
    }
    
    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return get(readAsync(count, id, keyToId));
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return readAsync(count, -1, null, id, keyToId);
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2) {
        return readAsync(count, timeout, unit, id, Collections.singletonMap(key2, id2));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2, String key3, StreamMessageId id3) {
        Map<String, StreamMessageId> params = new HashMap<String, StreamMessageId>(2);
        params.put(key2, id2);
        params.put(key3, id3);
        return readAsync(count, timeout, unit, id, params);
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return readAsync(0, timeout, unit, id, keyToId);
    }
    
    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return get(readAsync(count, timeout, unit, id, keyToId));
    }
    
    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        return read(0, timeout, unit, id, keyToId);
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(StreamMessageId id, String key2, StreamMessageId id2) {
        return readAsync(id, Collections.singletonMap(key2, id2));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3) {
        Map<String, StreamMessageId> params = new HashMap<String, StreamMessageId>(2);
        params.put(key2, id2);
        params.put(key3, id3);
        return readAsync(id, params);
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, StreamMessageId id, String key2, StreamMessageId id2) {
        return readAsync(count, id, Collections.singletonMap(key2, id2));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, StreamMessageId id, String key2, StreamMessageId id2,
            String key3, StreamMessageId id3) {
        Map<String, StreamMessageId> params = new HashMap<String, StreamMessageId>(2);
        params.put(key2, id2);
        params.put(key3, id3);
        return readAsync(count, id, params);
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2) {
        return readAsync(timeout, unit, id, Collections.singletonMap(key2, id2));
    }

    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(long timeout, TimeUnit unit, StreamMessageId id,
            String key2, StreamMessageId id2, String key3, StreamMessageId id3) {
        Map<String, StreamMessageId> params = new HashMap<String, StreamMessageId>(2);
        params.put(key2, id2);
        params.put(key3, id3);
        return readAsync(timeout, unit, id, params);
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(StreamMessageId id, String key2, StreamMessageId id2) {
        return get(readAsync(id, key2, id2));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3) {
        return get(readAsync(id, key2, id2, key3, id3));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, StreamMessageId id, String key2, StreamMessageId id2) {
        return get(readAsync(count, id, key2, id2));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, StreamMessageId id, String key2, StreamMessageId id2, String key3,
            StreamMessageId id3) {
        return get(readAsync(count, id, key2, id2, key3, id3));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2) {
        return get(readAsync(timeout, unit, id, key2, id2));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2, String key3, StreamMessageId id3) {
        return get(readAsync(timeout, unit, id, key2, id2, key3, id3));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2) {
        return get(readAsync(count, timeout, unit, id, key2, id2));
    }

    @Override
    public Map<String, Map<StreamMessageId, Map<K, V>>> read(int count, long timeout, TimeUnit unit, StreamMessageId id, String key2,
            StreamMessageId id2, String key3, StreamMessageId id3) {
        return get(readAsync(count, timeout, unit, id, key2, id2, key3, id3));
    }
    
    @Override
    public RFuture<Map<String, Map<StreamMessageId, Map<K, V>>>> readAsync(int count, long timeout, TimeUnit unit, StreamMessageId id, Map<String, StreamMessageId> keyToId) {
        List<Object> params = new ArrayList<Object>();
        if (count > 0) {
            params.add("COUNT");
            params.add(count);
        }
        
        if (timeout > 0) {
            params.add("BLOCK");
            params.add(unit.toMillis(timeout));
        }
        
        params.add("STREAMS");
        params.add(getRawName());
        params.addAll(keyToId.keySet());
        
        params.add(id);
        for (StreamMessageId nextId : keyToId.values()) {
            params.add(nextId.toString());
        }

        if (timeout > 0) {
            return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREAD_BLOCKING, params.toArray());
        }
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREAD, params.toArray());
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

    @Override
    public RFuture<StreamMessageId> addAsync(K key, V value) {
        return addAsync(key, value, 0, false);
    }

    @Override
    public RFuture<Void> addAsync(StreamMessageId id, K key, V value) {
        return addAsync(id, key, value, 0, false);
    }

    @Override
    public RFuture<StreamMessageId> addAsync(K key, V value, int trimLen, boolean trimStrict) {
        return addCustomAsync(null, key, value, trimLen, trimStrict);
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
    public RFuture<Void> addAsync(StreamMessageId id, K key, V value, int trimLen, boolean trimStrict) {
        return addCustomAsync(id, key, value, trimLen, trimStrict);
    }

    @Override
    public StreamMessageId add(K key, V value) {
        return get(addAsync(key, value));
    }

    @Override
    public void add(StreamMessageId id, K key, V value) {
        get(addAsync(id, key, value));
    }

    @Override
    public StreamMessageId add(K key, V value, int trimLen, boolean trimStrict) {
        return get(addAsync(key, value, trimLen, trimStrict));
    }

    @Override
    public void add(StreamMessageId id, K key, V value, int trimLen, boolean trimStrict) {
        get(addAsync(id, key, value, trimLen, trimStrict));
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(int count, StreamMessageId... ids) {
        return readAsync(count, 0, null, ids);
    }
    
    @Override
    public Map<StreamMessageId, Map<K, V>> read(int count, long timeout, TimeUnit unit, StreamMessageId... ids) {
        return get(readAsync(count, timeout, unit, ids));
    }
    
    @Override
    public Map<StreamMessageId, Map<K, V>> read(int count, StreamMessageId... ids) {
        return get(readAsync(count, ids));
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(int count, long timeout, TimeUnit unit,
            StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        if (count > 0) {
            params.add("COUNT");
            params.add(count);
        }
        
        if (timeout > 0) {
            params.add("BLOCK");
            params.add(unit.toMillis(timeout));
        }
        
        params.add("STREAMS");
        params.add(getRawName());

        for (StreamMessageId id : ids) {
            params.add(id.toString());
        }

        if (timeout > 0) {
            return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREAD_BLOCKING_SINGLE, params.toArray());
        }
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.XREAD_SINGLE, params.toArray());
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
    public RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(StreamMessageId... ids) {
        return readAsync(0, ids);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> readAsync(long timeout, TimeUnit unit, StreamMessageId... ids) {
        return readAsync(0, timeout, unit, ids);
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
    public Map<StreamMessageId, Map<K, V>> read(StreamMessageId... ids) {
        return read(0, ids);
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> read(long timeout, TimeUnit unit, StreamMessageId... ids) {
        return read(0, timeout, unit, ids);
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
    public long trim(TrimStrategy strategy, int threshold) {
        return get(trimAsync(strategy, threshold));
    }

    @Override
    public long trimNonStrict(TrimStrategy strategy, int threshold, int limit) {
        return get(trimNonStrictAsync(strategy, threshold, limit));
    }

    @Override
    public RFuture<Long> trimAsync(TrimStrategy strategy, int threshold) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XTRIM,
                                            getRawName(), strategy.toString(), threshold);
    }

    @Override
    public RFuture<Long> trimNonStrictAsync(TrimStrategy strategy, int threshold, int limit) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XTRIM,
                                    getRawName(), strategy.toString(), "~", threshold, "LIMIT", limit);
    }

    @Override
    public long trimNonStrict(TrimStrategy strategy, int threshold) {
        return get(trimNonStrictAsync(strategy, threshold));
    }

    @Override
    public RFuture<Long> trimNonStrictAsync(TrimStrategy strategy, int threshold) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XTRIM,
                                    getRawName(), strategy.toString(), "~", threshold);
    }

    @Override
    public RFuture<Long> trimAsync(int count) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XTRIM, getRawName(), "MAXLEN", count);
    }

    @Override
    public RFuture<Long> trimNonStrictAsync(int count) {
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XTRIM, getRawName(), "MAXLEN", "~", count);
    }

    @Override
    public long trim(int count) {
        return get(trimAsync(count));
    }

    @Override
    public long trimNonStrict(int count) {
        return get(trimNonStrictAsync(count));
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

    public RFuture<Long> trimAsync(StreamTrimArgs args) {
        return trimAsync(args, true);
    }

    private RFuture<Long> trimAsync(StreamTrimArgs args, boolean trimStrict) {
        StreamTrimParams pps = (StreamTrimParams) args;

        List<Object> params = new LinkedList<>();
        params.add(getRawName());

        if (pps.getMaxLen() > 0) {
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

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XTRIM, params.toArray());
    }

    @Override
    public long trimNonStrict(StreamTrimArgs args) {
        return get(trimNonStrictAsync(args));
    }

    public RFuture<Long> trimNonStrictAsync(StreamTrimArgs args) {
        return trimAsync(args, false);
    }

}
