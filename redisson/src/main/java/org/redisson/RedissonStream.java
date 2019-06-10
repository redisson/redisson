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
package org.redisson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.PendingEntry;
import org.redisson.api.PendingResult;
import org.redisson.api.RFuture;
import org.redisson.api.RStream;
import org.redisson.api.StreamConsumer;
import org.redisson.api.StreamGroup;
import org.redisson.api.StreamInfo;
import org.redisson.api.StreamMessageId;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListMultiDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.StreamInfoDecoder;
import org.redisson.client.protocol.decoder.StreamInfoMapDecoder;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonStream<K, V> extends RedissonExpirable implements RStream<K, V> {

    private final RedisCommand<StreamInfo<Object, Object>> xinfoStreamCommand;
    
    public RedissonStream(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
        xinfoStreamCommand = new RedisCommand<StreamInfo<Object, Object>>("XINFO", "STREAM",
                new ListMultiDecoder(new StreamInfoMapDecoder(getCodec()), new ObjectListReplayDecoder<String>(ListMultiDecoder.RESET), new StreamInfoDecoder()));
    }

    public RedissonStream(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
        xinfoStreamCommand = new RedisCommand<StreamInfo<Object, Object>>("XINFO", "STREAM",
                new ListMultiDecoder(new StreamInfoMapDecoder(getCodec()), new ObjectListReplayDecoder<String>(ListMultiDecoder.RESET), new StreamInfoDecoder()));
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
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XGROUP, "CREATE", getName(), groupName, id, "MKSTREAM");
    }
    
    @Override
    public RFuture<Long> ackAsync(String groupName, StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        params.add(getName());
        params.add(groupName);
        for (StreamMessageId id : ids) {
            params.add(id);
        }
        
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XACK, params.toArray());
    }

    @Override
    public long ack(String groupName, StreamMessageId... id) {
        return get(ackAsync(groupName, id));
    }

    @Override
    public RFuture<PendingResult> listPendingAsync(String groupName) {
        return getPendingInfoAsync(groupName);
    }

    @Override
    public PendingResult listPending(String groupName) {
        return getPendingInfo(groupName);
    }

    @Override
    public RFuture<PendingResult> getPendingInfoAsync(String groupName) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.XPENDING, getName(), groupName);
    }

    @Override
    public PendingResult getPendingInfo(String groupName) {
        return get(listPendingAsync(groupName));
    }
    
    @Override
    public RFuture<List<PendingEntry>> listPendingAsync(String groupName, String consumerName, StreamMessageId startId, StreamMessageId endId, int count) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.XPENDING_ENTRIES, getName(), groupName, startId, endId, count, consumerName);
    }

    @Override
    public RFuture<List<PendingEntry>> listPendingAsync(String groupName, StreamMessageId startId, StreamMessageId endId, int count) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.XPENDING_ENTRIES, getName(), groupName, startId, endId, count);
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
    public List<StreamMessageId> fastClaim(String groupName, String consumerName, long idleTime, TimeUnit idleTimeUnit,
            StreamMessageId... ids) {
        return get(fastClaimAsync(groupName, consumerName, idleTime, idleTimeUnit, ids));
    }
    
    @Override
    public RFuture<List<StreamMessageId>> fastClaimAsync(String groupName, String consumerName, long idleTime,
            TimeUnit idleTimeUnit, StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        params.add(getName());
        params.add(groupName);
        params.add(consumerName);
        params.add(idleTimeUnit.toMillis(idleTime));
        
        for (StreamMessageId id : ids) {
            params.add(id.toString());
        }
        
        params.add("JUSTID");
        
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.XCLAIM_IDS, params.toArray());
    }
    
    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> claimAsync(String groupName, String consumerName, long idleTime,
            TimeUnit idleTimeUnit, StreamMessageId... ids) {
        List<Object> params = new ArrayList<Object>();
        params.add(getName());
        params.add(groupName);
        params.add(consumerName);
        params.add(idleTimeUnit.toMillis(idleTime));
        
        for (StreamMessageId id : ids) {
            params.add(id.toString());
        }
        
        return commandExecutor.readAsync(getName(), codec, RedisCommands.XCLAIM, params.toArray());
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
            params.add(toSeconds(timeout, unit)*1000);
        }
        
        params.add("STREAMS");
        params.add(getName());

        if (ids.length == 0) {
            params.add(">");
        }
        
        for (StreamMessageId id : ids) {
            params.add(id.toString());
        }

        if (timeout > 0) {
            return commandExecutor.readAsync(getName(), codec, RedisCommands.XREADGROUP_BLOCKING_SINGLE, params.toArray());
        }
        return commandExecutor.readAsync(getName(), codec, RedisCommands.XREADGROUP_SINGLE, params.toArray());
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
            params.add(toSeconds(timeout, unit)*1000);
        }
        
        params.add("STREAMS");
        params.add(getName());
        for (String key : keyToId.keySet()) {
            params.add(key);
        }

        if (id == null) {
            params.add(">");
        } else {
            params.add(id);
        }
        
        for (StreamMessageId nextId : keyToId.values()) {
            params.add(nextId.toString());
        }

        if (timeout > 0) {
            return commandExecutor.readAsync(getName(), codec, RedisCommands.XREADGROUP_BLOCKING, params.toArray());
        }
        return commandExecutor.readAsync(getName(), codec, RedisCommands.XREADGROUP, params.toArray());
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
        params.add(getName());
        
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
            return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XADD, params.toArray());
        }
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XADD_VOID, params.toArray());
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
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XLEN, getName());
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
            params.add(toSeconds(timeout, unit)*1000);
        }
        
        params.add("STREAMS");
        params.add(getName());
        for (String key : keyToId.keySet()) {
            params.add(key);
        }
        
        params.add(id);
        for (StreamMessageId nextId : keyToId.values()) {
            params.add(nextId.toString());
        }

        if (timeout > 0) {
            return commandExecutor.readAsync(getName(), codec, RedisCommands.XREAD_BLOCKING, params.toArray());
        }
        return commandExecutor.readAsync(getName(), codec, RedisCommands.XREAD, params.toArray());
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
        params.add(getName());
        
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
            return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XADD, params.toArray());
        }
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XADD_VOID, params.toArray());
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
            params.add(toSeconds(timeout, unit)*1000);
        }
        
        params.add("STREAMS");
        params.add(getName());

        for (StreamMessageId id : ids) {
            params.add(id.toString());
        }

        if (timeout > 0) {
            return commandExecutor.readAsync(getName(), codec, RedisCommands.XREAD_BLOCKING_SINGLE, params.toArray());
        }
        return commandExecutor.readAsync(getName(), codec, RedisCommands.XREAD_SINGLE, params.toArray());
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> rangeAsync(int count, StreamMessageId startId, StreamMessageId endId) {
        List<Object> params = new LinkedList<Object>();
        params.add(getName());
        params.add(startId);
        params.add(endId);
        
        if (count > 0) {
            params.add("COUNT");
            params.add(count);
        }
        
        return commandExecutor.readAsync(getName(), codec, RedisCommands.XRANGE, params.toArray());
    }

    @Override
    public Map<StreamMessageId, Map<K, V>> range(int count, StreamMessageId startId, StreamMessageId endId) {
        return get(rangeAsync(count, startId, endId));
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> rangeReversedAsync(int count, StreamMessageId startId, StreamMessageId endId) {
        List<Object> params = new LinkedList<Object>();
        params.add(getName());
        params.add(startId);
        params.add(endId);
        
        if (count > 0) {
            params.add("COUNT");
            params.add(count);
        }
        
        return commandExecutor.readAsync(getName(), codec, RedisCommands.XREVRANGE, params.toArray());
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
        params.add(getName());
        for (StreamMessageId id : ids) {
            params.add(id);
        }

        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XDEL, params.toArray());
    }

    @Override
    public long remove(StreamMessageId... ids) {
        return get(removeAsync(ids));
    }

    @Override
    public RFuture<Long> trimAsync(int count) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XTRIM, "MAXLEN", count);
    }

    @Override
    public RFuture<Long> trimNonStrictAsync(int count) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XTRIM, "MAXLEN", "~", count);
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
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XGROUP, "DESTROY", getName(), groupName);
    }

    @Override
    public void removeGroup(String groupName) {
        get(removeGroupAsync(groupName));
    }

    @Override
    public RFuture<Long> removeConsumerAsync(String groupName, String consumerName) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XGROUP_LONG, "DELCONSUMER", getName(), groupName, consumerName);
    }

    @Override
    public long removeConsumer(String groupName, String consumerName) {
        return get(removeConsumerAsync(groupName, consumerName));
    }

    @Override
    public RFuture<Void> updateGroupMessageIdAsync(String groupName, StreamMessageId id) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.XGROUP, "SETID", getName(), groupName, id);
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
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, xinfoStreamCommand, getName());
    }

    @Override
    public List<StreamGroup> listGroups() {
        return get(listGroupsAsync());
    }

    @Override
    public RFuture<List<StreamGroup>> listGroupsAsync() {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.XINFO_GROUPS, getName());
    }

    @Override
    public List<StreamConsumer> listConsumers(String groupName) {
        return get(listConsumersAsync(groupName));
    }

    @Override
    public RFuture<List<StreamConsumer>> listConsumersAsync(String groupName) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.XINFO_CONSUMERS, getName(), groupName);
    }

    private static final RedisCommand<Map<StreamMessageId, Map<Object, Object>>> EVAL_XRANGE = new RedisCommand("EVAL", RedisCommands.XRANGE.getReplayMultiDecoder());
    
    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, StreamMessageId startId,
            StreamMessageId endId, int count) {
        return commandExecutor.evalReadAsync(getName(), codec, EVAL_XRANGE,
                "local pendingData = redis.call('xpending', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4]);" +
                "local result = {}; " +
                "for i = 1, #pendingData, 1 do " +
                    "local value = redis.call('xrange', KEYS[1], pendingData[i][1], pendingData[i][1]);" +
                    "table.insert(result, value[1]);" + 
                "end; " +
                "return result;",
                Collections.<Object>singletonList(getName()), 
                groupName, startId, endId, count);
    }

    @Override
    public RFuture<Map<StreamMessageId, Map<K, V>>> pendingRangeAsync(String groupName, String consumerName,
            StreamMessageId startId, StreamMessageId endId, int count) {
        return commandExecutor.evalReadAsync(getName(), codec, EVAL_XRANGE,
                "local pendingData = redis.call('xpending', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5]);" +
                "local result = {}; " +
                "for i = 1, #pendingData, 1 do " +
                    "local value = redis.call('xrange', KEYS[1], pendingData[i][1], pendingData[i][1]);" +
                    "table.insert(result, value[1]);" + 
                "end; " +
                "return result;",
                Collections.<Object>singletonList(getName()), 
                groupName, startId, endId, count, consumerName);
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

}
