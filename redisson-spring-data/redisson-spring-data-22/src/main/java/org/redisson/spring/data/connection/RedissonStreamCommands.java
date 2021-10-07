/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.*;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonStreamCommands implements RedisStreamCommands {

    private final RedissonConnection connection;

    public RedissonStreamCommands(RedissonConnection connection) {
        this.connection = connection;
    }

    private static List<String> toStringList(RecordId... recordIds) {
        if (recordIds.length == 1) {
            return Arrays.asList(recordIds[0].getValue());
        }

        return Arrays.stream(recordIds).map(RecordId::getValue).collect(Collectors.toList());
    }

    @Override
    public Long xAck(byte[] key, String group, RecordId... recordIds) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(group, "Group must not be null!");
        Assert.notNull(recordIds, "recordIds must not be null!");

        List<Object> params = new ArrayList<>();
        params.add(key);
        params.add(group);
        params.addAll(toStringList(recordIds));

		return connection.write(key, StringCodec.INSTANCE, RedisCommands.XACK, params.toArray());
    }

    private static final RedisStrictCommand<RecordId> XADD = new RedisStrictCommand<RecordId>("XADD", obj -> RecordId.of(obj.toString()));

    @Override
    public RecordId xAdd(MapRecord<byte[], byte[], byte[]> record) {
        Assert.notNull(record, "record must not be null!");

        List<Object> params = new LinkedList<>();
        params.add(record.getStream());
        params.add(record.getId().getValue());

        record.getValue().forEach((key, value) -> {
            params.add(key);
            params.add(value);
        });

        return connection.write(record.getStream(), StringCodec.INSTANCE, XADD, params.toArray());
    }

    @Override
    public Long xDel(byte[] key, RecordId... recordIds) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(recordIds, "recordIds must not be null!");

        List<Object> params = new ArrayList<>();
        params.add(key);
        params.addAll(toStringList(recordIds));

        return connection.write(key, StringCodec.INSTANCE, RedisCommands.XDEL, params.toArray());
    }

    private static final RedisStrictCommand<String> XGROUP_STRING = new RedisStrictCommand<>("XGROUP");

    @Override
    public String xGroupCreate(byte[] key, String groupName, ReadOffset readOffset) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(groupName, "GroupName must not be null!");
        Assert.notNull(readOffset, "ReadOffset must not be null!");

        return connection.write(key, StringCodec.INSTANCE, XGROUP_STRING, "CREATE", key, groupName, readOffset.getOffset(), "MKSTREAM");
    }

    private static final RedisStrictCommand<Boolean> XGROUP_BOOLEAN = new RedisStrictCommand<Boolean>("XGROUP", obj -> ((Long)obj) > 0);

    @Override
    public Boolean xGroupDelConsumer(byte[] key, Consumer consumer) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(consumer, "Consumer must not be null!");
        Assert.notNull(consumer.getName(), "Consumer name must not be null!");
        Assert.notNull(consumer.getGroup(), "Consumer group must not be null!");

        return connection.write(key, StringCodec.INSTANCE, XGROUP_BOOLEAN, "DELCONSUMER", key, consumer.getGroup(), consumer.getName());
    }

    @Override
    public Boolean xGroupDestroy(byte[] key, String groupName) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(groupName, "GroupName must not be null!");

        return connection.write(key, StringCodec.INSTANCE, XGROUP_BOOLEAN, "DESTROY", key, groupName);
    }

    @Override
    public Long xLen(byte[] key) {
        Assert.notNull(key, "Key must not be null!");

        return connection.write(key, StringCodec.INSTANCE, RedisCommands.XLEN, key);
    }

    private List<ByteRecord>  range(RedisCommand<?> rangeCommand, byte[] key, Range<String> range, RedisZSetCommands.Limit limit) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(range, "Range must not be null!");
        Assert.notNull(limit, "Limit must not be null!");

        List<Object> params = new LinkedList<>();
        params.add(key);

        if (rangeCommand.getName().equals(RedisCommands.XRANGE.getName())) {
            params.add(range.getLowerBound().getValue().orElse("-"));
            params.add(range.getUpperBound().getValue().orElse("+"));
        } else {
            params.add(range.getUpperBound().getValue().orElse("+"));
            params.add(range.getLowerBound().getValue().orElse("-"));
        }

        if (limit.getCount() > 0) {
            params.add("COUNT");
            params.add(limit.getCount());
        }

        return connection.write(key, ByteArrayCodec.INSTANCE, rangeCommand, params.toArray());
    }

    private static class ByteRecordReplayDecoder implements MultiDecoder<List<ByteRecord>> {

        private final byte[] key;

        ByteRecordReplayDecoder(byte[] key) {
            this.key = key;
        }

        @Override
        public List<ByteRecord> decode(List<Object> parts, State state) {
            List<List<Object>> list = (List<List<Object>>) (Object) parts;
            List<ByteRecord> result = new ArrayList<>(parts.size()/2);
            for (List<Object> entry : list) {
                ByteRecord record = StreamRecords.newRecord()
                        .in(key)
                        .withId(RecordId.of(entry.get(0).toString()))
                        .ofBytes((Map<byte[], byte[]>) entry.get(1));
                result.add(record);
            }
            return result;
        }
    }

    @Override
    public List<ByteRecord> xRange(byte[] key, Range<String> range, RedisZSetCommands.Limit limit) {
        return range(new RedisCommand<>("XRANGE",
            new ListMultiDecoder2(
                    new ByteRecordReplayDecoder(key),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder()))),
                key, range, limit);
    }

    private static class ByteRecordReplayDecoder2 implements MultiDecoder<List<ByteRecord>> {

        @Override
        public List<ByteRecord> decode(List<Object> parts, State state) {
            List<List<Object>> list = (List<List<Object>>) (Object) parts;
            List<ByteRecord> result = new ArrayList<>(parts.size()/2);

            for (List<Object> entries : list) {
                List<List<Object>> streamEntries = (List<List<Object>>) entries.get(1);
                if (streamEntries.isEmpty()) {
                    continue;
                }

                String name = (String) entries.get(0);
                for (List<Object> se : streamEntries) {
                    ByteRecord record = StreamRecords.newRecord()
                                            .in(name.getBytes())
                                            .withId(RecordId.of(se.get(0).toString()))
                                            .ofBytes((Map<byte[], byte[]>) se.get(1));
                    result.add(record);
                }
            }
            return result;
        }
    }


    private static final RedisCommand<List<ByteRecord>> XREAD = new RedisCommand<>("XREAD",
            new ListMultiDecoder2(
                    new ByteRecordReplayDecoder2(),
                    new ObjectDecoder(StringCodec.INSTANCE.getValueDecoder()),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder())));

    private static final RedisCommand<List<ByteRecord>> XREAD_BLOCKING =
            new RedisCommand<>("XREAD", XREAD.getReplayMultiDecoder());

    private static final RedisCommand<List<ByteRecord>> XREADGROUP =
            new RedisCommand<>("XREADGROUP", XREAD.getReplayMultiDecoder());

    private static final RedisCommand<List<ByteRecord>> XREADGROUP_BLOCKING =
            new RedisCommand<>("XREADGROUP", XREADGROUP.getReplayMultiDecoder());


    static {
        RedisCommands.BLOCKING_COMMANDS.add(XREAD_BLOCKING);
        RedisCommands.BLOCKING_COMMANDS.add(XREADGROUP_BLOCKING);
    }

    @Override
    public List<ByteRecord> xRead(StreamReadOptions readOptions, StreamOffset<byte[]>... streams) {
        Assert.notNull(readOptions, "ReadOptions must not be null!");
        Assert.notNull(streams, "StreamOffsets must not be null!");

        List<Object> params = new ArrayList<>();

        if (readOptions.getCount() != null && readOptions.getCount() > 0) {
            params.add("COUNT");
            params.add(readOptions.getCount());
        }

        if (readOptions.getBlock() != null && readOptions.getBlock() > 0) {
            params.add("BLOCK");
            params.add(readOptions.getBlock());
        }

        params.add("STREAMS");
        for (StreamOffset<byte[]> streamOffset : streams) {
            params.add(streamOffset.getKey());
        }

        for (StreamOffset<byte[]> streamOffset : streams) {
            params.add(streamOffset.getOffset().getOffset());
        }

        if (readOptions.getBlock() != null && readOptions.getBlock() > 0) {
            return connection.read(streams[0].getKey(), ByteArrayCodec.INSTANCE, XREAD_BLOCKING, params.toArray());
        }
        return connection.read(streams[0].getKey(), ByteArrayCodec.INSTANCE, XREAD, params.toArray());
    }

    @Override
    public List<ByteRecord> xReadGroup(Consumer consumer, StreamReadOptions readOptions, StreamOffset<byte[]>... streams) {
        Assert.notNull(readOptions, "Consumer must not be null!");
        Assert.notNull(readOptions, "ReadOptions must not be null!");
        Assert.notNull(streams, "StreamOffsets must not be null!");

        List<Object> params = new ArrayList<>();

        params.add("GROUP");
        params.add(consumer.getGroup());
        params.add(consumer.getName());

        if (readOptions.getCount() != null && readOptions.getCount() > 0) {
            params.add("COUNT");
            params.add(readOptions.getCount());
        }

        if (readOptions.getBlock() != null && readOptions.getBlock() > 0) {
            params.add("BLOCK");
            params.add(readOptions.getBlock());
        }

        if (readOptions.isNoack()) {
            params.add("NOACK");
        }

        params.add("STREAMS");
        for (StreamOffset<byte[]> streamOffset : streams) {
            params.add(streamOffset.getKey());
        }

        for (StreamOffset<byte[]> streamOffset : streams) {
            params.add(streamOffset.getOffset().getOffset());
        }

        if (readOptions.getBlock() != null && readOptions.getBlock() > 0) {
            return connection.write(streams[0].getKey(), ByteArrayCodec.INSTANCE, XREADGROUP_BLOCKING, params.toArray());
        }
        return connection.write(streams[0].getKey(), ByteArrayCodec.INSTANCE, XREADGROUP, params.toArray());
    }

    @Override
    public List<ByteRecord> xRevRange(byte[] key, Range<String> range, RedisZSetCommands.Limit limit) {
        return range(new RedisCommand<>("XREVRANGE",
            new ListMultiDecoder2(
                    new ByteRecordReplayDecoder(key),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder()))),
                key, range, limit);
    }

    @Override
    public Long xTrim(byte[] key, long count) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(count, "Count must not be null!");

        return connection.write(key, StringCodec.INSTANCE, RedisCommands.XTRIM, key, "MAXLEN", count);
    }

}
