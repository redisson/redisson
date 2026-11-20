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
package org.redisson.spring.data.connection;

import org.redisson.api.BatchResult;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanNotNullReplayConvertor;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.command.CommandBatchService;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.RedisJsonCommands;
import org.springframework.data.redis.connection.json.JsonPath;
import org.springframework.data.redis.connection.json.JsonSetCondition;
import org.springframework.data.redis.connection.json.JsonType;
import org.springframework.data.redis.connection.json.JsonValue;
import org.springframework.util.Assert;

import java.util.*;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonJsonCommands implements RedisJsonCommands {

    private final RedissonConnection connection;

    public RedissonJsonCommands(RedissonConnection connection) {
        this.connection = connection;
    }

    private static final RedisCommand<List<Long>> JSON_ARRAPPEND = new RedisCommand<>("JSON.ARRAPPEND", new ObjectListReplayDecoder<>());

    @Override
    public List<Long> jsonArrAppend(byte[] key, JsonPath path, JsonValue... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");
        Assert.notEmpty(values, "Values must not be empty");
        Assert.noNullElements(values, "Values must not be null");

        List<Object> params = new ArrayList<>(values.length + 2);
        params.add(key);
        params.add(path.asString());
        for (JsonValue value : values) {
            params.add(value.asBytes());
        }
        return connection.write(key, StringCodec.INSTANCE, JSON_ARRAPPEND, params.toArray());
    }

    private static final RedisCommand<List<Long>> JSON_ARRINDEX = new RedisCommand<>("JSON.ARRINDEX", new ObjectListReplayDecoder<>());

    @Override
    public List<Long> jsonArrIndex(byte[] key, JsonPath path, JsonValue value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");
        Assert.notNull(value, "Value must not be null");

        return connection.read(key, StringCodec.INSTANCE, JSON_ARRINDEX, key, path.asString(), value.asBytes());
    }

    private static final RedisCommand<List<Long>> JSON_ARRINSERT = new RedisCommand<>("JSON.ARRINSERT", new ObjectListReplayDecoder<>());

    @Override
    public List<Long> jsonArrInsert(byte[] key, JsonPath path, int index, JsonValue... values) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");
        Assert.notEmpty(values, "Values must not be empty");
        Assert.noNullElements(values, "Values must not be null");

        List<Object> params = new ArrayList<>(values.length + 3);
        params.add(key);
        params.add(path.asString());
        params.add(index);
        for (JsonValue value : values) {
            params.add(value.asBytes());
        }
        return connection.write(key, StringCodec.INSTANCE, JSON_ARRINSERT, params.toArray());
    }

    private static final RedisCommand<List<Long>> JSON_ARRLEN = new RedisCommand<>("JSON.ARRLEN", new ObjectListReplayDecoder<>());

    @Override
    public List<Long> jsonArrLen(byte[] key, JsonPath path) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");

        return connection.read(key, StringCodec.INSTANCE, JSON_ARRLEN, key, path.asString());
    }

    private static final RedisCommand<List<Long>> JSON_ARRTRIM = new RedisCommand<>("JSON.ARRTRIM", new ObjectListReplayDecoder<>());

    @Override
    public List<Long> jsonArrTrim(byte[] key, JsonPath path, int start, int stop) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");

        return connection.write(key, StringCodec.INSTANCE, JSON_ARRTRIM, key, path.asString(), start, stop);
    }

    @Override
    public Long jsonClear(byte[] key, JsonPath path) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");

        return connection.write(key, StringCodec.INSTANCE, RedisCommands.JSON_CLEAR, key, path.asString());
    }

    @Override
    public Long jsonDel(byte[] key, JsonPath path) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");

        return connection.write(key, StringCodec.INSTANCE, RedisCommands.JSON_DEL_LONG, key, path.asString());
    }

    private static final RedisCommand<byte[]> JSON_GET = new RedisCommand<>("JSON.GET");

    @Override
    public byte[] jsonGet(byte[] key, JsonPath... paths) {
        Assert.notNull(key, "Key must not be null");
        Assert.notEmpty(paths, "Paths must not be empty");
        Assert.noNullElements(paths, "Paths must not be null");

        List<Object> params = new ArrayList<>(paths.length + 1);
        params.add(key);
        for (JsonPath path : paths) {
            params.add(path.asString());
        }
        return connection.read(key, ByteArrayCodec.INSTANCE, JSON_GET, params.toArray());
    }

    private static final RedisStrictCommand<Boolean> JSON_MERGE = new RedisStrictCommand<>("JSON.MERGE", new BooleanNotNullReplayConvertor());

    @Override
    public Boolean jsonMerge(byte[] key, JsonPath path, JsonValue value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");
        Assert.notNull(value, "Value must not be null");

        return connection.write(key, StringCodec.INSTANCE, JSON_MERGE, key, path.asString(), value.asBytes());
    }

    private static final RedisCommand<List<byte[]>> JSON_MGET = new RedisCommand<>("JSON.MGET", new ObjectListReplayDecoder<>());

    @Override
    public List<byte[]> jsonMGet(JsonPath path, byte[]... keys) {
        Assert.notNull(path, "Path must not be null");
        Assert.notEmpty(keys, "Keys must not be empty");
        Assert.noNullElements(keys, "Keys must not be null");

        int[] slots = new int[keys.length];
        Map<Integer, List<Object>> paramsBySlot = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            slots[i] = connection.executorService.getConnectionManager().calcSlot(keys[i]);
            paramsBySlot.computeIfAbsent(slots[i], slot -> new ArrayList<>()).add(keys[i]);
        }

        if (paramsBySlot.size() == 1) {
            List<Object> params = paramsBySlot.get(slots[0]);
            params.add(path.asString());
            return connection.read(keys[0], ByteArrayCodec.INSTANCE, JSON_MGET, params.toArray());
        }
        if (connection.isQueueing() || connection.isPipelined()) {
            throw new InvalidDataAccessApiUsageException("JSON.MGET keys must hash to the same slot in a pipeline or transaction");
        }

        CommandBatchService es = new CommandBatchService(connection.executorService);
        for (List<Object> params : paramsBySlot.values()) {
            byte[] firstKey = (byte[]) params.get(0);
            params.add(path.asString());
            es.readAsync(firstKey, ByteArrayCodec.INSTANCE, JSON_MGET, params.toArray());
        }
        BatchResult<?> result = connection.syncFuture(es.executeAsync());

        Map<Integer, Iterator<byte[]>> valuesBySlot = new HashMap<>();
        Iterator<?> responses = result.getResponses().iterator();
        for (Integer slot : paramsBySlot.keySet()) {
            valuesBySlot.put(slot, ((List<byte[]>) responses.next()).iterator());
        }

        List<byte[]> values = new ArrayList<>(keys.length);
        for (int slot : slots) {
            values.add(valuesBySlot.get(slot).next());
        }
        return values;
    }

    @Override
    public Boolean jsonSet(byte[] key, JsonPath path, JsonValue value, JsonSetCondition condition) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");
        Assert.notNull(value, "Value must not be null");
        Assert.notNull(condition, "Condition must not be null");

        List<Object> params = new ArrayList<>(4);
        params.add(key);
        params.add(path.asString());
        params.add(value.asBytes());
        switch (condition.getPathCondition()) {
            case UPSERT:
                break;
            case IF_PATH_NOT_EXISTS:
                params.add("NX");
                break;
            case IF_PATH_EXISTS:
                params.add("XX");
                break;
            default:
                throw new IllegalArgumentException("Unsupported path condition: " + condition.getPathCondition());
        }
        return connection.write(key, StringCodec.INSTANCE, RedisCommands.JSON_SET_BOOLEAN, params.toArray());
    }

    private static final RedisCommand<List<Long>> JSON_STRAPPEND = new RedisCommand<>("JSON.STRAPPEND", new ObjectListReplayDecoder<>());

    @Override
    public List<Long> jsonStrAppend(byte[] key, JsonPath path, String value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");
        Assert.notNull(value, "Value must not be null");

        return connection.write(key, StringCodec.INSTANCE, JSON_STRAPPEND, key, path.asString(), JsonValue.of(value).asBytes());
    }

    private static final RedisCommand<List<Long>> JSON_STRLEN = new RedisCommand<>("JSON.STRLEN", new ObjectListReplayDecoder<>());

    @Override
    public List<Long> jsonStrLen(byte[] key, JsonPath path) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");

        return connection.read(key, StringCodec.INSTANCE, JSON_STRLEN, key, path.asString());
    }

    @Override
    public List<Boolean> jsonToggle(byte[] key, JsonPath path) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");

        return connection.write(key, StringCodec.INSTANCE, RedisCommands.JSON_TOGGLE_LIST, key, path.asString());
    }

    private static final Convertor<JsonType> JSON_TYPE_CONVERTOR = obj -> {
        if (obj == null) {
            return null;
        }
        String type = obj.toString();
        if ("integer".equals(type)) {
            return JsonType.NUMBER;
        }
        return JsonType.valueOf(type.toUpperCase(Locale.ENGLISH));
    };

    private static final MultiDecoder<Object> JSON_TYPE_DECODER = (parts, state) -> {
        if (parts.size() == 1) {
            Object first = parts.get(0);
            if (first == null) {
                return null;
            }
            if (first instanceof List) {
                return first;
            }
        }
        return parts;
    };

    private static final RedisCommand<List<JsonType>> JSON_TYPE = new RedisCommand("JSON.TYPE", JSON_TYPE_DECODER, JSON_TYPE_CONVERTOR);

    @Override
    public List<JsonType> jsonType(byte[] key, JsonPath path) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(path, "Path must not be null");

        return connection.read(key, StringCodec.INSTANCE, JSON_TYPE, key, path.asString());
    }

}
