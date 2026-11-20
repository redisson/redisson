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

import org.reactivestreams.Publisher;
import org.redisson.api.stream.*;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.*;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.ReactiveStreamCommands;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveStreamCommands extends RedissonBaseReactive implements ReactiveStreamCommands {

    RedissonReactiveStreamCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    private static List<String> toStringList(List<RecordId> recordIds) {
        return recordIds.stream().map(RecordId::getValue).collect(Collectors.toList());
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<XClaimCommand, Flux<RecordId>>> xClaimJustId(Publisher<XClaimCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGroupName(), "Group name must not be null!");
            Assert.notNull(command.getNewOwner(), "NewOwner must not be null!");
            Assert.notEmpty(command.getOptions().getIds(), "Ids collection must not be empty!");

            List<Object> params = new ArrayList<>();
            byte[] k = toByteArray(command.getKey());
            params.add(k);
            params.add(command.getGroupName());
            params.add(command.getNewOwner());
            params.add(Objects.requireNonNull(command.getOptions().getMinIdleTime()).toMillis());
            RedissonStreamCommands.appendXClaimOptions(params, command.getOptions());
            params.add("JUSTID");

            Mono<List<StreamMessageId>> m = write(k, ByteArrayCodec.INSTANCE, RedisCommands.XCLAIM_IDS, params.toArray());
            return m.map(ids -> new ReactiveRedisConnection.CommandResponse<>(command,
                    Flux.fromIterable(ids).map(id -> RecordId.of(id.toString()))));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<XClaimCommand, Flux<ByteBufferRecord>>> xClaim(Publisher<XClaimCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGroupName(), "Group name must not be null!");
            Assert.notNull(command.getNewOwner(), "NewOwner must not be null!");
            Assert.notEmpty(command.getOptions().getIds(), "Ids collection must not be empty!");

            List<Object> params = new ArrayList<>();
            byte[] k = toByteArray(command.getKey());
            params.add(k);
            params.add(command.getGroupName());
            params.add(command.getNewOwner());
            params.add(Objects.requireNonNull(command.getOptions().getMinIdleTime()).toMillis());
            RedissonStreamCommands.appendXClaimOptions(params, command.getOptions());

            Mono<Map<StreamMessageId, Map<byte[], byte[]>>> m = write(k, ByteArrayCodec.INSTANCE, RedisCommands.XCLAIM, params.toArray());
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, Flux.fromStream(v.entrySet().stream()).map(e -> {
                Map<ByteBuffer, ByteBuffer> map = e.getValue().entrySet().stream()
                        .collect(Collectors.toMap(entry -> ByteBuffer.wrap(entry.getKey()),
                                entry -> ByteBuffer.wrap(entry.getValue())));
                return StreamRecords.newRecord()
                        .in(command.getKey())
                        .withId(RecordId.of(e.getKey().toString()))
                        .ofBuffer(map);
            })));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<PendingRecordsCommand, PendingMessagesSummary>> xPendingSummary(Publisher<PendingRecordsCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGroupName(), "Group name must not be null!");

            byte[] k = toByteArray(command.getKey());

            Mono<PendingResult> m = write(k, StringCodec.INSTANCE, RedisCommands.XPENDING, k, command.getGroupName());
            return m.map(v -> {
                PendingMessagesSummary s;
                if (v.getTotal() == 0) {
                    s = new PendingMessagesSummary(command.getGroupName(), 0, Range.unbounded(), v.getConsumerNames());

                } else {
                    Range<String> range = Range.open(v.getLowestId().toString(), v.getHighestId().toString());
                    s = new PendingMessagesSummary(command.getGroupName(), v.getTotal(), range, v.getConsumerNames());
                }
                return new ReactiveRedisConnection.CommandResponse<>(command, s);
            });
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<PendingRecordsCommand, PendingMessages>> xPending(Publisher<PendingRecordsCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGroupName(), "Group name must not be null!");

            byte[] k = toByteArray(command.getKey());

            List<Object> params = new ArrayList<>();
            params.add(k);
            params.add(command.getGroupName());

            if (command.getMinIdleTime() != null) {
                params.add("IDLE");
                params.add(command.getMinIdleTime().toMillis());
            }

            params.add(((Range.Bound<String>)command.getRange().getLowerBound()).getValue().orElse("-"));
            params.add(((Range.Bound<String>)command.getRange().getUpperBound()).getValue().orElse("+"));

            if (command.getCount() != null) {
                params.add(command.getCount());
            } else {
                params.add(Long.MAX_VALUE);
            }
            if (command.getConsumerName() != null) {
                params.add(command.getConsumerName());
            }

            Mono<List<PendingEntry>> m = write(k, StringCodec.INSTANCE, RedisCommands.XPENDING_ENTRIES, params.toArray());
            return m.map(list -> {
                List<PendingMessage> msgs = list.stream().map(v -> new PendingMessage(RecordId.of(v.getId().toString()),
                        Consumer.from(command.getGroupName(), v.getConsumerName()),
                        Duration.of(v.getIdleTime(), ChronoUnit.MILLIS),
                        v.getDeliveryCount())).collect(Collectors.toList());
                PendingMessages s = new PendingMessages(command.getGroupName(), command.getRange(), msgs);
                return new ReactiveRedisConnection.CommandResponse<>(command, s);
            });
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<XInfoCommand, StreamInfo.XInfoStream>> xInfo(Publisher<XInfoCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] k = toByteArray(command.getKey());

            Mono<StreamInfo.XInfoStream> m = write(k, StringCodec.INSTANCE, RedissonStreamCommands.XINFO_STREAM, k);
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, v));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<XInfoCommand, Flux<StreamInfo.XInfoGroup>>> xInfoGroups(Publisher<XInfoCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] k = toByteArray(command.getKey());

            Mono<StreamInfo.XInfoGroups> m = write(k, StringCodec.INSTANCE, RedissonStreamCommands.XINFO_GROUPS, k);
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, Flux.fromIterable(v)));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<XInfoCommand, Flux<StreamInfo.XInfoConsumer>>> xInfoConsumers(Publisher<XInfoCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGroupName(), "Group name must not be null!");

            byte[] k = toByteArray(command.getKey());

            Mono<StreamInfo.XInfoConsumers> m = write(k, StringCodec.INSTANCE,
                                    RedissonStreamCommands.xInfoConsumersCommand(command.getGroupName()), k, command.getGroupName());
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, Flux.fromIterable(v)));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.NumericResponse<AcknowledgeCommand, Long>> xAck(Publisher<AcknowledgeCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGroup(), "Group must not be null!");
            Assert.notNull(command.getRecordIds(), "recordIds must not be null!");

            List<Object> params = new ArrayList<>();
            byte[] k = toByteArray(command.getKey());
            params.add(k);
            params.add(command.getGroup());
            params.addAll(toStringList(command.getRecordIds()));

            Mono<Long> m = write(k, StringCodec.INSTANCE, RedisCommands.XACK, params.toArray());
            return m.map(v -> new ReactiveRedisConnection.NumericResponse<>(command, v));
        });
    }

    private static final RedisStrictCommand<RecordId> XADD = new RedisStrictCommand<>("XADD",
            id -> id != null ? RecordId.of(id.toString()) : null);

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<AddStreamRecord, RecordId>> xAdd(Publisher<AddStreamRecord> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getBody(), "Body must not be null!");
            Assert.notNull(command.getOptions(), "Options must not be null!");

            byte[] k = toByteArray(command.getKey());
            List<Object> params = new LinkedList<>();
            params.add(k);

            RedisStreamCommands.XAddOptions options = command.getOptions();
            if (options.isNoMkStream()) {
                params.add("NOMKSTREAM");
            }

            RedisStreamCommands.TrimOptions trimOptions = options.getTrimOptions();
            if (trimOptions != null) {
                if (trimOptions.hasDeletionPolicy()) {
                    params.add(toDeletionPolicy(trimOptions.getDeletionPolicy()));
                }
                RedissonStreamCommands.appendTrimClause(params, trimOptions);
            }

            if (!command.getRecord().getId().shouldBeAutoGenerated()) {
                params.add(command.getRecord().getId().getValue());
            } else {
                params.add("*");
            }

            for (Map.Entry<ByteBuffer, ByteBuffer> entry : command.getBody().entrySet()) {
                params.add(toByteArray(entry.getKey()));
                params.add(toByteArray(entry.getValue()));
            }

            Mono<RecordId> m = write(k, StringCodec.INSTANCE, XADD, params.toArray());
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, v));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<DeleteCommand, Long>> xDel(Publisher<DeleteCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRecordIds(), "recordIds must not be null!");

            byte[] k = toByteArray(command.getKey());
            List<Object> params = new ArrayList<>();
            params.add(k);
            params.addAll(toStringList(command.getRecordIds()));

            Mono<Long> m = write(k, StringCodec.INSTANCE, RedisCommands.XDEL, params.toArray());
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, v));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.NumericResponse<ReactiveRedisConnection.KeyCommand, Long>> xLen(Publisher<ReactiveRedisConnection.KeyCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] k = toByteArray(command.getKey());

            Mono<Long> m = write(k, StringCodec.INSTANCE, RedisCommands.XLEN, k);
            return m.map(v -> new ReactiveRedisConnection.NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<RangeCommand, Flux<ByteBufferRecord>>> xRange(Publisher<RangeCommand> publisher) {
        return range(RedisCommands.XRANGE, publisher);
    }

    private Flux<ReactiveRedisConnection.CommandResponse<RangeCommand, Flux<ByteBufferRecord>>> range(RedisCommand<?> rangeCommand, Publisher<RangeCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");
            Assert.notNull(command.getLimit(), "Limit must not be null!");

            byte[] k = toByteArray(command.getKey());

            List<Object> params = new LinkedList<>();
            params.add(k);

            if (rangeCommand.getName().equals(RedisCommands.XRANGE.getName())) {
                params.add(toLowerBound(command.getRange()));
                params.add(toUpperBound(command.getRange()));
            } else {
                params.add(toUpperBound(command.getRange()));
                params.add(toLowerBound(command.getRange()));
            }

            if (command.getLimit().getCount() > 0) {
                params.add("COUNT");
                params.add(command.getLimit().getCount());
            }

            Mono<Map<StreamMessageId, Map<byte[], byte[]>>> m = write(k, ByteArrayCodec.INSTANCE, rangeCommand, params.toArray());
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, Flux.fromStream(v.entrySet().stream()).map(e -> {
                Map<ByteBuffer, ByteBuffer> map = e.getValue().entrySet().stream()
                        .collect(Collectors.toMap(entry -> ByteBuffer.wrap(entry.getKey()),
                                entry -> ByteBuffer.wrap(entry.getValue())));
                return StreamRecords.newRecord()
                        .in(command.getKey())
                        .withId(RecordId.of(e.getKey().toString()))
                        .ofBuffer(map);
            })));
        });
    }

    String toLowerBound(Range range) {
        StringBuilder s = new StringBuilder();
        if (!range.getLowerBound().isInclusive()) {
            s.append("(");
        }
        if (!range.getLowerBound().getValue().isPresent() || range.getLowerBound().getValue().get().toString()
                .isEmpty()) {
            s.append("-");
        } else {
            s.append(range.getLowerBound().getValue().get());
        }
        return s.toString();
    }

    String toUpperBound(Range range) {
        StringBuilder s = new StringBuilder();
        if (!range.getUpperBound().isInclusive()) {
            s.append("(");
        }
        if (!range.getUpperBound().getValue().isPresent() || range.getUpperBound().getValue().get().toString()
                .isEmpty()) {
            s.append("+");
        } else {
            s.append(range.getUpperBound().getValue().get());
        }
        return s.toString();
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<ReadCommand, Flux<ByteBufferRecord>>> read(Publisher<ReadCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getStreamOffsets(), "StreamOffsets must not be null!");
            Assert.notNull(command.getReadOptions(), "ReadOptions must not be null!");

            List<Object> params = new ArrayList<>();

            if (command.getConsumer() != null) {
                params.add("GROUP");
                params.add(command.getConsumer().getGroup());
                params.add(command.getConsumer().getName());
            }

            if (command.getReadOptions().getCount() != null && command.getReadOptions().getCount() > 0) {
                params.add("COUNT");
                params.add(command.getReadOptions().getCount());
            }

            if (command.getReadOptions().getBlock() != null && command.getReadOptions().getBlock() > 0) {
                params.add("BLOCK");
                params.add(command.getReadOptions().getBlock());
            }

            if (command.getConsumer() != null && command.getReadOptions().isNoack()) {
                params.add("NOACK");
            }

            params.add("STREAMS");
            for (StreamOffset<ByteBuffer> streamOffset : command.getStreamOffsets()) {
                params.add(toByteArray(streamOffset.getKey()));
            }

            for (StreamOffset<ByteBuffer> streamOffset : command.getStreamOffsets()) {
                params.add(streamOffset.getOffset().getOffset());
            }

            byte[] key = toByteArray(command.getStreamOffsets().get(0).getKey());
            boolean blocking = command.getReadOptions().getBlock() != null && command.getReadOptions().getBlock() > 0;
            Mono<List<ByteRecord>> m;
            if (command.getConsumer() == null) {
                RedisCommand<List<ByteRecord>> xread;
                if (executorService.getServiceManager().isResp3()) {
                    if (blocking) {
                        xread = RedissonStreamCommands.XREAD_BLOCKING_V2;
                    } else {
                        xread = RedissonStreamCommands.XREAD_V2;
                    }
                } else {
                    if (blocking) {
                        xread = RedissonStreamCommands.XREAD_BLOCKING;
                    } else {
                        xread = RedissonStreamCommands.XREAD;
                    }
                }
                m = read(key, ByteArrayCodec.INSTANCE, xread, params.toArray());
            } else {
                RedisCommand<List<ByteRecord>> xreadgroup;
                if (executorService.getServiceManager().isResp3()) {
                    if (blocking) {
                        xreadgroup = RedissonStreamCommands.XREADGROUP_BLOCKING_V2;
                    } else {
                        xreadgroup = RedissonStreamCommands.XREADGROUP_V2;
                    }
                } else {
                    if (blocking) {
                        xreadgroup = RedissonStreamCommands.XREADGROUP_BLOCKING;
                    } else {
                        xreadgroup = RedissonStreamCommands.XREADGROUP;
                    }
                }
                m = write(key, ByteArrayCodec.INSTANCE, xreadgroup, params.toArray());
            }

            return m.map(records -> new ReactiveRedisConnection.CommandResponse<>(command, Flux.fromIterable(records)
                    .map(record -> {
                        Map<ByteBuffer, ByteBuffer> fields = new LinkedHashMap<>();
                        for (Map.Entry<byte[], byte[]> field : record.getValue().entrySet()) {
                            fields.put(ByteBuffer.wrap(field.getKey()), ByteBuffer.wrap(field.getValue()));
                        }
                        return StreamRecords.newRecord()
                                .in(record.getStream())
                                .withId(record.getId())
                                .ofBuffer(fields);
                    })));

        });
    }

    private static final RedisStrictCommand<String> XGROUP_STRING = new RedisStrictCommand<>("XGROUP");

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<GroupCommand, String>> xGroup(Publisher<GroupCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGroupName(), "GroupName must not be null!");

            byte[] k = toByteArray(command.getKey());

            if (command.getAction().equals(GroupCommand.GroupCommandAction.CREATE)) {
                Assert.notNull(command.getReadOffset(), "ReadOffset must not be null!");

                List<Object> params = new ArrayList<>();
                params.add("CREATE");
                params.add(k);
                params.add(command.getGroupName());
                params.add(command.getReadOffset().getOffset());
                if (command.isMkStream()) {
                    params.add("MKSTREAM");
                }

                Mono<String> m = write(k, StringCodec.INSTANCE, XGROUP_STRING, params.toArray());
                return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, v));
            }

            if (command.getAction().equals(GroupCommand.GroupCommandAction.DELETE_CONSUMER)) {
                Assert.notNull(command.getConsumerName(), "ConsumerName must not be null!");

                Mono<Long> m = write(k, StringCodec.INSTANCE, RedisCommands.XGROUP_LONG, "DELCONSUMER", k, command.getGroupName(), command.getConsumerName());
                return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, "OK"));
            }

            if (command.getAction().equals(GroupCommand.GroupCommandAction.DESTROY)) {
                Mono<Long> m = write(k, StringCodec.INSTANCE, RedisCommands.XGROUP_LONG, "DESTROY", k, command.getGroupName());
                return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, v > 0 ? "OK" : "Error"));
            }

            throw new IllegalArgumentException("unknown command " + command.getAction());
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<RangeCommand, Flux<ByteBufferRecord>>> xRevRange(Publisher<RangeCommand> publisher) {
        return range(RedisCommands.XREVRANGE, publisher);
    }

    @Override
    public Flux<ReactiveRedisConnection.NumericResponse<ReactiveRedisConnection.KeyCommand, Long>> xTrim(Publisher<TrimCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getOptions(), "Options must not be null!");

            byte[] k = toByteArray(command.getKey());
            RedisStreamCommands.TrimOptions trimOptions = command.getOptions().getTrimOptions();

            List<Object> params = new ArrayList<>();
            params.add(k);
            RedissonStreamCommands.appendTrimClause(params, trimOptions);

            if (trimOptions.hasDeletionPolicy()) {
                params.add(toDeletionPolicy(trimOptions.getDeletionPolicy()));
            }

            Mono<Long> m = write(k, StringCodec.INSTANCE, RedisCommands.XTRIM, params.toArray());
            return m.map(v -> new ReactiveRedisConnection.NumericResponse<>(command, v));
        });
    }

    private static final RedisCommand<List<Long>> XACKDEL = new RedisCommand<>("XACKDEL", new ObjectListReplayDecoder<>());

    private static final RedisCommand<List<Long>> XDELEX = new RedisCommand<>("XDELEX", new ObjectListReplayDecoder<>());

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<AcknowledgeDeleteCommand, List<RedisStreamCommands.StreamEntryDeletionResult>>> xAckDel(Publisher<AcknowledgeDeleteCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGroup(), "Group must not be null!");
            Assert.notNull(command.getRecordIds(), "recordIds must not be null!");

            byte[] k = toByteArray(command.getKey());

            List<Object> params = new ArrayList<>();
            params.add(k);
            params.add(command.getGroup());
            params.add(toDeletionPolicy(command.getOptions().getDeletionPolicy()));
            params.add("IDS");
            params.add(command.getRecordIds().size());
            params.addAll(toStringList(command.getRecordIds()));

            Mono<List<Long>> m = write(k, StringCodec.INSTANCE, XACKDEL, params.toArray());
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, toDeletionResults(v)));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.CommandResponse<DeleteExCommand, List<RedisStreamCommands.StreamEntryDeletionResult>>> xDelEx(Publisher<DeleteExCommand> publisher) {
        return execute(publisher, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRecordIds(), "recordIds must not be null!");

            byte[] k = toByteArray(command.getKey());

            List<Object> params = new ArrayList<>();
            params.add(k);
            params.add(toDeletionPolicy(command.getOptions().getDeletionPolicy()));
            params.add("IDS");
            params.add(command.getRecordIds().size());
            params.addAll(toStringList(command.getRecordIds()));

            Mono<List<Long>> m = write(k, StringCodec.INSTANCE, XDELEX, params.toArray());
            return m.map(v -> new ReactiveRedisConnection.CommandResponse<>(command, toDeletionResults(v)));
        });
    }

    private static String toDeletionPolicy(RedisStreamCommands.StreamDeletionPolicy policy) {
        switch (policy) {
            case DELETE_REFERENCES:
                return "DELREF";
            case ACKNOWLEDGED:
                return "ACKED";
            case KEEP_REFERENCES:
            default:
                return "KEEPREF";
        }
    }

    private static List<RedisStreamCommands.StreamEntryDeletionResult> toDeletionResults(List<Long> codes) {
        return codes.stream()
                .map(c -> RedisStreamCommands.StreamEntryDeletionResult.fromCode(c))
                .collect(Collectors.toList());
    }

}
