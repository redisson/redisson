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
package org.redisson.spring.data.connection;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.DoubleNullSafeReplayConvertor;
import org.redisson.client.protocol.decoder.ListMultiDecoder;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ListScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.LongMultiDecoder;
import org.redisson.client.protocol.decoder.ObjectSetReplayDecoder;
import org.redisson.reactive.CommandReactiveExecutor;
import org.redisson.reactive.SetReactiveIterator;
import org.springframework.data.domain.Range;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.redis.connection.DefaultTuple;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyScanCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.data.redis.connection.ReactiveZSetCommands;
import org.springframework.data.redis.connection.RedisZSetCommands.Tuple;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveZSetCommands extends RedissonBaseReactive implements ReactiveZSetCommands {

    RedissonReactiveZSetCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }
    
    private static final RedisCommand<Double> ZADD_FLOAT = new RedisCommand<Double>("ZADD", new DoubleNullSafeReplayConvertor());

    @Override
    public Flux<NumericResponse<ZAddCommand, Number>> zAdd(Publisher<ZAddCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notEmpty(command.getTuples(), "Tuples must not be empty or null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            
            List<Object> params = new ArrayList<Object>(command.getTuples().size()*2+1);
            params.add(keyBuf);
            if (command.isIncr() || command.isUpsert() || command.isReturnTotalChanged()) {
                if (command.isUpsert()) {
                    params.add("NX");
                } else {
                    params.add("XX");
                }
                if (command.isReturnTotalChanged()) {
                    params.add("CH");
                }
                if (command.isIncr()) {
                    params.add("INCR");
                }
            }
            
            for (Tuple entry : command.getTuples()) {
                params.add(BigDecimal.valueOf(entry.getScore()).toPlainString());
                params.add(entry.getValue());
            }

            Mono<Number> m;
            if (command.isIncr()) {
                m = write(keyBuf, DoubleCodec.INSTANCE, ZADD_FLOAT, params.toArray());
            } else {
                m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.ZADD, params.toArray());
            }
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<ZRemCommand, Long>> zRem(Publisher<ZRemCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValues(), "Values must not be null!");

            List<Object> args = new ArrayList<Object>(command.getValues().size() + 1);
            args.add(toByteArray(command.getKey()));
            args.addAll(command.getValues().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));
            
            Mono<Long> m = write((byte[])args.get(0), StringCodec.INSTANCE, RedisCommands.ZREM_LONG, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<ZIncrByCommand, Double>> zIncrBy(Publisher<ZIncrByCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Member must not be null!");
            Assert.notNull(command.getIncrement(), "Increment value must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            Mono<Double> m = write(keyBuf, DoubleCodec.INSTANCE, RedisCommands.ZINCRBY, keyBuf, new BigDecimal(command.getIncrement().doubleValue()).toPlainString(), valueBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<ZRankCommand, Long>> zRank(Publisher<ZRankCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Member must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            RedisCommand<Long> cmd = RedisCommands.ZRANK;
            if (command.getDirection() == Direction.DESC) {
                cmd = RedisCommands.ZREVRANK;
            }
            Mono<Long> m = read(keyBuf, DoubleCodec.INSTANCE, cmd, keyBuf, valueBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    private static final RedisCommand<Set<Tuple>> ZRANGE_ENTRY = new RedisCommand<Set<Tuple>>("ZRANGE", new ScoredSortedSetReplayDecoder());
    private static final RedisCommand<Set<Object>> ZRANGE = new RedisCommand<Set<Object>>("ZRANGE", new ObjectSetReplayDecoder<Object>());
    private static final RedisCommand<Set<Tuple>> ZREVRANGE_ENTRY = new RedisCommand<Set<Tuple>>("ZREVRANGE", new ScoredSortedSetReplayDecoder());
    private static final RedisCommand<Set<Object>> ZREVRANGE = new RedisCommand<Set<Object>>("ZREVRANGE", new ObjectSetReplayDecoder<Object>());
    
    @Override
    public Flux<CommandResponse<ZRangeCommand, Flux<Tuple>>> zRange(Publisher<ZRangeCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());

            long start = command.getRange().getLowerBound().getValue().orElse(0L);
            long end = command.getRange().getUpperBound().getValue().get();
            
            Flux<Tuple> flux;
            if (command.getDirection() == Direction.ASC) {
                if (command.isWithScores()) {
                    Mono<Set<Tuple>> m = read(keyBuf, ByteArrayCodec.INSTANCE, ZRANGE_ENTRY, 
                                keyBuf, start, end, "WITHSCORES");
                    flux = m.flatMapMany(e -> Flux.fromIterable(e));
                } else {
                    Mono<Set<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, ZRANGE, keyBuf, start, end);
                    flux = m.flatMapMany(e -> Flux.fromIterable(e).map(b -> new DefaultTuple(b, Double.NaN)));
                }
            } else {
                if (command.isWithScores()) {
                    Mono<Set<Tuple>> m = read(keyBuf, ByteArrayCodec.INSTANCE, ZREVRANGE_ENTRY, 
                                keyBuf, start, end, "WITHSCORES");
                    flux = m.flatMapMany(e -> Flux.fromIterable(e));
                } else {
                    Mono<Set<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, ZREVRANGE, keyBuf, start, end);
                    flux = m.flatMapMany(e -> Flux.fromIterable(e).map(b -> new DefaultTuple(b, Double.NaN)));
                }
            }
            return Mono.just(new CommandResponse<>(command, flux));
        });
    }

    private static final RedisCommand<Set<Tuple>> ZRANGEBYSCORE = new RedisCommand<Set<Tuple>>("ZRANGEBYSCORE", new ScoredSortedSetReplayDecoder());
    private static final RedisCommand<Set<Tuple>> ZREVRANGEBYSCORE = new RedisCommand<Set<Tuple>>("ZREVRANGEBYSCORE", new ScoredSortedSetReplayDecoder());
    
    @Override
    public Flux<CommandResponse<ZRangeByScoreCommand, Flux<Tuple>>> zRangeByScore(
            Publisher<ZRangeByScoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());

            String start = toLowerBound(command.getRange(), "-inf");
            String end = toUpperBound(command.getRange(), "+inf");
            
            List<Object> args = new ArrayList<Object>();
            args.add(keyBuf);
            if (command.getDirection() == Direction.ASC) {
                args.add(start);
            } else {
                args.add(end);
            }
            if (command.getDirection() == Direction.ASC) {
                args.add(end);
            } else {
                args.add(start);
            }
            if (command.isWithScores()) {
                args.add("WITHSCORES");
            }
            if (command.getLimit().isPresent() && !command.getLimit().get().isUnlimited()) {
                args.add("LIMIT");
                args.add(command.getLimit().get().getOffset());
                args.add(command.getLimit().get().getCount());
            }

            Flux<Tuple> flux;
            if (command.getDirection() == Direction.ASC) {
                if (command.isWithScores()) {
                    Mono<Set<Tuple>> m = read(keyBuf, ByteArrayCodec.INSTANCE, ZRANGEBYSCORE, args.toArray());
                    flux = m.flatMapMany(e -> Flux.fromIterable(e));
                } else {
                    Mono<Set<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.ZRANGEBYSCORE, args.toArray());
                    flux = m.flatMapMany(e -> Flux.fromIterable(e).map(b -> new DefaultTuple(b, Double.NaN)));
                }
            } else {
                if (command.isWithScores()) {
                    Mono<Set<Tuple>> m = read(keyBuf, ByteArrayCodec.INSTANCE, ZREVRANGEBYSCORE, args.toArray());
                    flux = m.flatMapMany(e -> Flux.fromIterable(e));
                } else {
                    Mono<Set<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.ZREVRANGEBYSCORE, args.toArray());
                    flux = m.flatMapMany(e -> Flux.fromIterable(e).map(b -> new DefaultTuple(b, Double.NaN)));
                }
            }

            return Mono.just(new CommandResponse<>(command, flux));
        });
    }

    private static final RedisCommand<ListScanResult<Tuple>> ZSCAN = new RedisCommand<ListScanResult<Tuple>>("ZSCAN", 
            new ListMultiDecoder(new LongMultiDecoder(), new ScoredSortedListReplayDecoder(), new ListScanResultReplayDecoder()));

    @Override
    public Flux<CommandResponse<KeyCommand, Flux<Tuple>>> zScan(Publisher<KeyScanCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getOptions(), "ScanOptions must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            Flux<Tuple> flux = Flux.create(new SetReactiveIterator<Tuple>() {
                @Override
                protected RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                    if (command.getOptions().getPattern() == null) {
                        return executorService.readAsync(client, keyBuf, ByteArrayCodec.INSTANCE, ZSCAN, 
                                keyBuf, nextIterPos, "COUNT", Optional.ofNullable(command.getOptions().getCount()).orElse(10L));
                    }

                    return executorService.readAsync(client, keyBuf, ByteArrayCodec.INSTANCE, ZSCAN, 
                                keyBuf, nextIterPos, "MATCH", command.getOptions().getPattern(), 
                                                    "COUNT", Optional.ofNullable(command.getOptions().getCount()).orElse(10L));
                }
            });
            return Mono.just(new CommandResponse<>(command, flux));
        });
    }

    private static final RedisStrictCommand<Long> ZCOUNT = new RedisStrictCommand<Long>("ZCOUNT");
    
    String toLowerBound(Range range, Object defaultValue) {
        StringBuilder s = new StringBuilder();
        if (!range.getLowerBound().isInclusive()) {
            s.append("(");
        }
        if (!range.getLowerBound().getValue().isPresent() || range.getLowerBound().getValue().get().toString().isEmpty()) {
            s.append(defaultValue);
        } else {
            s.append(range.getLowerBound().getValue().get());
        }
        return s.toString();
    }

    String toUpperBound(Range range, Object defaultValue) {
        StringBuilder s = new StringBuilder();
        if (!range.getUpperBound().isInclusive()) {
            s.append("(");
        }
        if (!range.getUpperBound().getValue().isPresent() || range.getUpperBound().getValue().get().toString().isEmpty()) {
            s.append(defaultValue);
        } else {
            s.append(range.getUpperBound().getValue().get());
        }
        return s.toString();
    }
    
    String toLexLowerBound(Range range, Object defaultValue) {
        StringBuilder s = new StringBuilder();
        if (range.getLowerBound().isInclusive()) {
            s.append("[");
        } else {
            s.append("(");
        }
        if (!range.getLowerBound().getValue().isPresent() || range.getLowerBound().getValue().get().toString().isEmpty()) {
            s.append(defaultValue);
        } else {
            s.append(range.getLowerBound().getValue().get());
        }
        return s.toString();
    }

    String toLexUpperBound(Range range, Object defaultValue) {
        StringBuilder s = new StringBuilder();
        if (range.getUpperBound().isInclusive()) {
            s.append("[");
        } else {
            s.append("(");
        }
        if (!range.getUpperBound().getValue().isPresent() || range.getUpperBound().getValue().get().toString().isEmpty()) {
            s.append(defaultValue);
        } else {
            s.append(range.getUpperBound().getValue().get());
        }
        return s.toString();
    }

    
    @Override
    public Flux<NumericResponse<ZCountCommand, Long>> zCount(Publisher<ZCountCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, ZCOUNT, 
                                keyBuf, toLowerBound(command.getRange(), 0),
                                toUpperBound(command.getRange(), -1));
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> zCard(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.ZCARD, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<ZScoreCommand, Double>> zScore(Publisher<ZScoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            Mono<Double> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.ZSCORE, keyBuf, valueBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    private static final RedisStrictCommand<Long> ZREMRANGEBYRANK = new RedisStrictCommand<Long>("ZREMRANGEBYRANK");
    
    @Override
    public Flux<NumericResponse<ZRemRangeByRankCommand, Long>> zRemRangeByRank(
            Publisher<ZRemRangeByRankCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, ZREMRANGEBYRANK, 
                                keyBuf, command.getRange().getLowerBound().getValue().orElse(0L),
                                command.getRange().getUpperBound().getValue().get());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }
    
    private static final RedisStrictCommand<Long> ZREMRANGEBYSCORE = new RedisStrictCommand<Long>("ZREMRANGEBYSCORE");

    @Override
    public Flux<NumericResponse<ZRemRangeByScoreCommand, Long>> zRemRangeByScore(
            Publisher<ZRemRangeByScoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, ZREMRANGEBYSCORE, 
                                keyBuf, toLowerBound(command.getRange(), 0),
                                toUpperBound(command.getRange(), -1));
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    private static final RedisStrictCommand<Long> ZUNIONSTORE = new RedisStrictCommand<Long>("ZUNIONSTORE");
    
    @Override
    public Flux<NumericResponse<ZUnionStoreCommand, Long>> zUnionStore(Publisher<ZUnionStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Destination key must not be null!");
            Assert.notEmpty(command.getSourceKeys(), "Source keys must not be null or empty!");

            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<Object>(command.getSourceKeys().size() * 2 + 5);
            args.add(keyBuf);
            args.add(command.getSourceKeys().size());
            args.addAll(command.getSourceKeys().stream().map(e -> toByteArray(e)).collect(Collectors.toList()));
            if (!command.getWeights().isEmpty()) {
                args.add("WEIGHTS");
                for (Double weight : command.getWeights()) {
                    args.add(BigDecimal.valueOf(weight).toPlainString());
                }
            }
            if (command.getAggregateFunction().isPresent()) {
                args.add("AGGREGATE");
                args.add(command.getAggregateFunction().get().name());
            }
            Mono<Long> m = write(keyBuf, LongCodec.INSTANCE, ZUNIONSTORE, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }
    
    private static final RedisStrictCommand<Long> ZINTERSTORE = new RedisStrictCommand<Long>("ZINTERSTORE");

    @Override
    public Flux<NumericResponse<ZInterStoreCommand, Long>> zInterStore(Publisher<ZInterStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Destination key must not be null!");
            Assert.notEmpty(command.getSourceKeys(), "Source keys must not be null or empty!");

            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<Object>(command.getSourceKeys().size() * 2 + 5);
            args.add(keyBuf);
            args.add(command.getSourceKeys().size());
            args.addAll(command.getSourceKeys().stream().map(e -> toByteArray(e)).collect(Collectors.toList()));
            if (!command.getWeights().isEmpty()) {
                args.add("WEIGHTS");
                for (Double weight : command.getWeights()) {
                    args.add(BigDecimal.valueOf(weight).toPlainString());
                }
            }
            if (command.getAggregateFunction().isPresent()) {
                args.add("AGGREGATE");
                args.add(command.getAggregateFunction().get().name());
            }
            Mono<Long> m = write(keyBuf, LongCodec.INSTANCE, ZINTERSTORE, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }
    
    private static final RedisCommand<Set<Object>> ZRANGEBYLEX = new RedisCommand<Set<Object>>("ZRANGEBYLEX", new ObjectSetReplayDecoder<Object>());
    private static final RedisCommand<Set<Object>> ZREVRANGEBYLEX = new RedisCommand<Set<Object>>("ZREVRANGEBYLEX", new ObjectSetReplayDecoder<Object>());

    @Override
    public Flux<CommandResponse<ZRangeByLexCommand, Flux<ByteBuffer>>> zRangeByLex(
            Publisher<ZRangeByLexCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());

            String start = null;
            String end = null;
            if (command.getDirection() == Direction.ASC) {
                start = toLexLowerBound(command.getRange(), "-");
                end = toLexUpperBound(command.getRange(), "+");
            } else {
                start = toLexUpperBound(command.getRange(), "-");
                end = toLexLowerBound(command.getRange(), "+");
            }
            
            Mono<Set<byte[]>> m;
            if (!command.getLimit().isUnlimited()) {
                if (command.getDirection() == Direction.ASC) {
                    m = read(keyBuf, ByteArrayCodec.INSTANCE, ZRANGEBYLEX, 
                                keyBuf, start, end, "LIMIT", command.getLimit().getOffset(), command.getLimit().getCount());
                } else {
                    m = read(keyBuf, ByteArrayCodec.INSTANCE, ZREVRANGEBYLEX, 
                                keyBuf, start, end, "LIMIT", command.getLimit().getOffset(), command.getLimit().getCount());
                }
            } else {
                if (command.getDirection() == Direction.ASC) {
                    m = read(keyBuf, ByteArrayCodec.INSTANCE, ZRANGEBYLEX, 
                                keyBuf, start, end);
                } else {
                    m = read(keyBuf, ByteArrayCodec.INSTANCE, ZREVRANGEBYLEX, 
                                keyBuf, start, end);
                }
            }
            Flux<ByteBuffer> flux = m.flatMapMany(e -> Flux.fromIterable(e).map(v -> ByteBuffer.wrap(v)));
            return Mono.just(new CommandResponse<>(command, flux));
        });
    }

}
