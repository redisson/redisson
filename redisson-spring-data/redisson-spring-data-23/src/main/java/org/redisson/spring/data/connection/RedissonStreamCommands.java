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

import org.redisson.api.*;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.StreamIdConvertor;
import org.redisson.client.protocol.decoder.*;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public RecordId xAdd(MapRecord<byte[], byte[], byte[]> record) {
		return xAdd(record, XAddOptions.none());
    }

    private static final RedisStrictCommand<RecordId> XCLAIM_JUSTID = new RedisStrictCommand<RecordId>("XCLAIM", obj -> RecordId.of(obj.toString()));

    @Override
    public List<RecordId> xClaimJustId(byte[] key, String group, String newOwner, XClaimOptions options) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(group, "Group name must not be null!");
        Assert.notNull(newOwner, "NewOwner must not be null!");
        Assert.notEmpty(options.getIds(), "Ids collection must not be empty!");

        List<Object> params = new ArrayList<>();
        params.add(key);
        params.add(group);
        params.add(newOwner);
        params.add(Objects.requireNonNull(options.getIdleTime()).toMillis());
        params.addAll(Arrays.asList(options.getIdsAsStringArray()));
        params.add("JUSTID");

        return connection.write(key, StringCodec.INSTANCE, XCLAIM_JUSTID, params.toArray());
    }

    @Override
    public List<ByteRecord> xClaim(byte[] key, String group, String newOwner, XClaimOptions options) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(group, "Group name must not be null!");
        Assert.notNull(newOwner, "NewOwner must not be null!");
        Assert.notEmpty(options.getIds(), "Ids collection must not be empty!");

        List<Object> params = new ArrayList<>();
        params.add(key);
        params.add(group);
        params.add(newOwner);
        params.add(Objects.requireNonNull(options.getIdleTime()).toMillis());
        params.addAll(Arrays.asList(options.getIdsAsStringArray()));

        return connection.write(key, ByteArrayCodec.INSTANCE, new RedisCommand<List<ByteRecord>>("XCLAIM",
                                        new ListMultiDecoder2(
                                                new ByteRecordReplayDecoder(key),
                                                new ObjectDecoder(new StreamIdDecoder()),
                                                new MapEntriesDecoder(new StreamObjectMapReplayDecoder()))), params.toArray());
    }

    @Override
    public String xGroupCreate(byte[] key, String groupName, ReadOffset readOffset, boolean mkStream) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(groupName, "GroupName must not be null!");
        Assert.notNull(readOffset, "ReadOffset must not be null!");

        List<Object> params = new ArrayList<>();
        params.add("CREATE");
        params.add(key);
        params.add(groupName);
        params.add(readOffset.getOffset());
        if (mkStream) {
            params.add("MKSTREAM");
        }

        return connection.write(key, StringCodec.INSTANCE, XGROUP_STRING, params.toArray());
    }

    private static class XInfoStreamReplayDecoder implements MultiDecoder<StreamInfo.XInfoStream> {

        @Override
        public StreamInfo.XInfoStream decode(List<Object> parts, State state) {
            Map<String, Object> res = new HashMap<>();
            res.put("length", parts.get(1));
            res.put("radix-tree-keys", parts.get(3));
            res.put("radix-tree-nodes", parts.get(5));
            res.put("groups", parts.get(7));
            res.put("last-generated-id", parts.get(9).toString());

            List<?> firstEntry = (List<?>) parts.get(11);
            if (firstEntry != null) {
                StreamMessageId firstId = StreamIdConvertor.INSTANCE.convert(firstEntry.get(0));
                Map<Object, Object> firstData = (Map<Object, Object>) firstEntry.get(1);
                res.put("first-entry", firstData);
            }

            List<?> lastEntry = (List<?>) parts.get(13);
            if (lastEntry != null) {
                StreamMessageId lastId = StreamIdConvertor.INSTANCE.convert(lastEntry.get(0));
                Map<Object, Object> lastData = (Map<Object, Object>) lastEntry.get(1);
                res.put("last-entry", lastData);
            }

            List<Object> list = res.entrySet().stream()
                                    .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                                    .collect(Collectors.toList());
            return StreamInfo.XInfoStream.fromList(list);
        }
    }

    private static final RedisCommand<org.redisson.api.StreamInfo<Object, Object>> XINFO_STREAM = new RedisCommand<>("XINFO", "STREAM",
            new ListMultiDecoder2(
                    new XInfoStreamReplayDecoder(),
                    new ObjectDecoder(StringCodec.INSTANCE.getValueDecoder()),
                    new ObjectMapDecoder(false)));

    @Override
    public StreamInfo.XInfoStream xInfo(byte[] key) {
        Assert.notNull(key, "Key must not be null!");


        return connection.write(key, ByteArrayCodec.INSTANCE, XINFO_STREAM, key);
    }

    private static class XInfoGroupsReplayDecoder implements MultiDecoder<StreamInfo.XInfoGroups> {

        @Override
        public StreamInfo.XInfoGroups decode(List<Object> parts, State state) {
            List<Object> result = new ArrayList<>();
            for (List<Object> part: (List<List<Object>>) (Object)parts) {
                Map<String, Object> res = new HashMap<>();
                res.put("name", part.get(1));
                res.put("consumers", part.get(3));
                res.put("pending", part.get(5));
                res.put("last-delivered-id", part.get(7));
                List<Object> list = res.entrySet().stream()
                        .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());
                result.add(list);
            }

            return StreamInfo.XInfoGroups.fromList(result);
        }
    }

    RedisCommand<StreamInfo.XInfoGroups> XINFO_GROUPS = new RedisCommand<>("XINFO", "GROUPS",
            new ListMultiDecoder2(new XInfoGroupsReplayDecoder(),
                            new ObjectListReplayDecoder(), new ObjectListReplayDecoder())
    );

    @Override
    public StreamInfo.XInfoGroups xInfoGroups(byte[] key) {
        return connection.write(key, StringCodec.INSTANCE, XINFO_GROUPS, key);
    }

    private static class XInfoConsumersReplayDecoder implements MultiDecoder<StreamInfo.XInfoConsumers> {

        private final String groupName;

        public XInfoConsumersReplayDecoder(String groupName) {
            this.groupName = groupName;
        }

        @Override
        public StreamInfo.XInfoConsumers decode(List<Object> parts, State state) {
            List<Object> result = new ArrayList<>();
            for (List<Object> part: (List<List<Object>>) (Object)parts) {
                Map<String, Object> res = new HashMap<>();
                res.put("name", part.get(1));
                res.put("pending", part.get(3));
                res.put("idle", part.get(5));
                List<Object> list = res.entrySet().stream()
                        .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());
                result.add(list);
            }

            return StreamInfo.XInfoConsumers.fromList(groupName, result);
        }
    }

    @Override
    public StreamInfo.XInfoConsumers xInfoConsumers(byte[] key, String groupName) {
        return connection.write(key, StringCodec.INSTANCE, new RedisCommand<StreamInfo.XInfoConsumers>("XINFO", "CONSUMERS",
                        new ListMultiDecoder2(new XInfoConsumersReplayDecoder(groupName),
                            new ObjectListReplayDecoder(), new ObjectListReplayDecoder())), key, groupName);
    }

    private static class PendingMessagesSummaryReplayDecoder implements MultiDecoder<PendingMessagesSummary> {

        private final String groupName;

        public PendingMessagesSummaryReplayDecoder(String groupName) {
            this.groupName = groupName;
        }

        @Override
        public PendingMessagesSummary decode(List<Object> parts, State state) {
            if (parts.isEmpty()) {
                return null;
            }

            List<List<String>> customerParts = (List<List<String>>) parts.get(3);
            if (customerParts.isEmpty()) {
                return new PendingMessagesSummary(groupName, 0, Range.unbounded(), Collections.emptyMap());
            }

            Map<String, Long> map = customerParts.stream().collect(Collectors.toMap(e -> e.get(0), e -> Long.valueOf(e.get(1)),
                    (u, v) -> { throw new IllegalStateException("Duplicate key: " + u); },
                    LinkedHashMap::new));
            Range<String> range = Range.open(parts.get(1).toString(), parts.get(2).toString());
            return new PendingMessagesSummary(groupName, (Long) parts.get(0), range, map);
        }
    }

    @Override
    public PendingMessagesSummary xPending(byte[] key, String groupName) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(groupName, "Group name must not be null!");

        return connection.write(key, StringCodec.INSTANCE, new RedisCommand<PendingMessagesSummary>("XPENDING",
                            new ListMultiDecoder2(new PendingMessagesSummaryReplayDecoder(groupName),
                            new ObjectListReplayDecoder(), new ObjectListReplayDecoder())), key, groupName);
    }

    private static class PendingMessageReplayDecoder implements MultiDecoder<PendingMessage> {

        private String groupName;

        public PendingMessageReplayDecoder(String groupName) {
            this.groupName = groupName;
        }

        @Override
        public PendingMessage decode(List<Object> parts, State state) {
            PendingMessage pm = new PendingMessage(RecordId.of(parts.get(0).toString()),
                    Consumer.from(groupName, parts.get(1).toString()),
                    Duration.of(Long.valueOf(parts.get(2).toString()), ChronoUnit.MILLIS),
                    Long.valueOf(parts.get(3).toString()));
            return pm;
        }
    }

    private static class PendingMessagesReplayDecoder implements MultiDecoder<PendingMessages> {

        private final String groupName;
        private final Range<?> range;

        public PendingMessagesReplayDecoder(String groupName, Range<?> range) {
            this.groupName = groupName;
            this.range = range;
        }

        @Override
        public PendingMessages decode(List<Object> parts, State state) {
            List<PendingMessage> pendingMessages = (List<PendingMessage>) (Object) parts;
            return new PendingMessages(groupName, range, pendingMessages);
        }
    }

    @Override
    public PendingMessages xPending(byte[] key, String groupName, XPendingOptions options) {
        Assert.notNull(key, "Key must not be null!");
        Assert.notNull(groupName, "Group name must not be null!");

        List<Object> params = new ArrayList<>();
        params.add(key);
        params.add(groupName);

        params.add(((Range.Bound<String>)options.getRange().getLowerBound()).getValue().orElse("-"));
        params.add(((Range.Bound<String>)options.getRange().getUpperBound()).getValue().orElse("+"));

        if (options.getCount() != null) {
            params.add(options.getCount());
        } else {
            params.add(10);
        }
        if (options.getConsumerName() != null) {
            params.add(options.getConsumerName());
        }

        return connection.write(key, StringCodec.INSTANCE, new RedisCommand<>("XPENDING",
                            new ListMultiDecoder2<PendingMessages>(
                            new PendingMessagesReplayDecoder(groupName, options.getRange()),
                            new PendingMessageReplayDecoder(groupName))),
        params.toArray());
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
    public RecordId xAdd(MapRecord<byte[], byte[], byte[]> record, XAddOptions options) {
        Assert.notNull(record, "record must not be null!");

        List<Object> params = new LinkedList<>();
        params.add(record.getStream());

        if (options.getMaxlen() != null) {
            params.add("MAXLEN");
            params.add(options.getMaxlen());
        }

        if (!record.getId().shouldBeAutoGenerated()) {
            params.add(record.getId().getValue());
        } else {
            params.add("*");
        }

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
        return xGroupCreate(key, groupName, readOffset, false);
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
