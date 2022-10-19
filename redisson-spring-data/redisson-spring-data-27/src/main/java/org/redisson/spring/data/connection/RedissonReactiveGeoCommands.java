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
package org.redisson.spring.data.connection;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.*;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.ReactiveGeoCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.MultiValueResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.domain.geo.BoxShape;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.RadiusShape;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveGeoCommands extends RedissonBaseReactive implements ReactiveGeoCommands {

    RedissonReactiveGeoCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Flux<NumericResponse<GeoAddCommand, Long>> geoAdd(Publisher<GeoAddCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getGeoLocations(), "Locations must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            
            List<Object> args = new ArrayList<Object>();
            args.add(keyBuf);
            for (GeoLocation<ByteBuffer> location : command.getGeoLocations()) {
                args.add(location.getPoint().getX());
                args.add(location.getPoint().getY());
                args.add(toByteArray(location.getName()));
            }
            
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.GEOADD, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<GeoDistCommand, Distance>> geoDist(Publisher<GeoDistCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getFrom(), "From member must not be null!");
            Assert.notNull(command.getTo(), "To member must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] fromBuf = toByteArray(command.getFrom());
            byte[] toBuf = toByteArray(command.getTo());
            
            Metric metric = RedisGeoCommands.DistanceUnit.METERS;
            if (command.getMetric().isPresent()) {
                metric = command.getMetric().get();
            }
            
            Mono<Distance> m = write(keyBuf, DoubleCodec.INSTANCE, new RedisCommand<Distance>("GEODIST", new DistanceConvertor(metric)), 
                                    keyBuf, fromBuf, toBuf, metric.getAbbreviation());
            return m.map(v -> new CommandResponse<>(command, v));
        });
    }
    
    private static final RedisCommand<List<Object>> GEOHASH = new RedisCommand<List<Object>>("GEOHASH", new ObjectListReplayDecoder<Object>());

    @Override
    public Flux<MultiValueResponse<GeoHashCommand, String>> geoHash(Publisher<GeoHashCommand> commands) {
        return execute(commands, command -> {
            
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getMembers(), "Members must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<Object>(command.getMembers().size() + 1);
            args.add(keyBuf);
            args.addAll(command.getMembers().stream().map(buf -> toByteArray(buf)).collect(Collectors.toList()));
            
            Mono<List<String>> m = read(keyBuf, StringCodec.INSTANCE, GEOHASH, args.toArray());
            return m.map(v -> new MultiValueResponse<>(command, v));
        });
    }

    private final MultiDecoder<Map<Object, Object>> geoDecoder = new ListMultiDecoder2(new ObjectListReplayDecoder2(), new PointDecoder());
    
    @Override
    public Flux<MultiValueResponse<GeoPosCommand, Point>> geoPos(Publisher<GeoPosCommand> commands) {
        return execute(commands, command -> {
            
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getMembers(), "Members must not be null!");
            
            RedisCommand<Map<Object, Object>> cmd = new RedisCommand<Map<Object, Object>>("GEOPOS", geoDecoder);
            
            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<Object>(command.getMembers().size() + 1);
            args.add(keyBuf);
            args.addAll(command.getMembers().stream().map(buf -> toByteArray(buf)).collect(Collectors.toList()));
            
            Mono<List<Point>> m = read(keyBuf, StringCodec.INSTANCE, cmd, args.toArray());
            return m.map(v -> new MultiValueResponse<>(command, v));
        });
    }

    private final MultiDecoder<GeoResults<GeoLocation<ByteBuffer>>> postitionDecoder = new ListMultiDecoder2(new ByteBufferGeoResultsDecoder(), new CodecDecoder(), new PointDecoder(), new ObjectListReplayDecoder());
    
    @Override
    public Flux<CommandResponse<GeoRadiusCommand, Flux<GeoResult<GeoLocation<ByteBuffer>>>>> geoRadius(
            Publisher<GeoRadiusCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getPoint(), "Point must not be null!");
            Assert.notNull(command.getDistance(), "Distance must not be null!");

            GeoRadiusCommandArgs args = command.getArgs().orElse(GeoRadiusCommandArgs.newGeoRadiusArgs());
            byte[] keyBuf = toByteArray(command.getKey());
            
            List<Object> params = new ArrayList<Object>();
            params.add(keyBuf);
            params.add(BigDecimal.valueOf(command.getPoint().getX()).toPlainString());
            params.add(BigDecimal.valueOf(command.getPoint().getY()).toPlainString());
            params.add(command.getDistance().getValue());
            params.add(command.getDistance().getMetric().getAbbreviation());
            
            RedisCommand<GeoResults<GeoLocation<ByteBuffer>>> cmd;
            if (args.getFlags().contains(GeoRadiusCommandArgs.Flag.WITHCOORD)) {
                cmd = new RedisCommand<>("GEORADIUS_RO", postitionDecoder);
                params.add("WITHCOORD");
            } else {
                MultiDecoder<GeoResults<GeoLocation<ByteBuffer>>> distanceDecoder = new ListMultiDecoder2(new ByteBufferGeoResultsDecoder(command.getDistance().getMetric()), new GeoDistanceDecoder());
                cmd = new RedisCommand<>("GEORADIUS_RO", distanceDecoder);
                params.add("WITHDIST");
            }
            
            if (args.getLimit() != null) {
                params.add("COUNT");
                params.add(args.getLimit());
            }
            if (args.getSortDirection() != null) {
                params.add(args.getSortDirection().name());
            }
            
            Mono<GeoResults<GeoLocation<ByteBuffer>>> m = read(keyBuf, ByteArrayCodec.INSTANCE, cmd, params.toArray());
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v.getContent())));
        });
    }

    @Override
    public Flux<CommandResponse<GeoRadiusByMemberCommand, Flux<GeoResult<GeoLocation<ByteBuffer>>>>> geoRadiusByMember(
            Publisher<GeoRadiusByMemberCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getMember(), "Member must not be null!");
            Assert.notNull(command.getDistance(), "Distance must not be null!");
            
            GeoRadiusCommandArgs args = command.getArgs().orElse(GeoRadiusCommandArgs.newGeoRadiusArgs());
            byte[] keyBuf = toByteArray(command.getKey());
            byte[] memberBuf = toByteArray(command.getMember());
            
            List<Object> params = new ArrayList<Object>();
            params.add(keyBuf);
            params.add(memberBuf);
            params.add(command.getDistance().getValue());
            params.add(command.getDistance().getMetric().getAbbreviation());
            
            RedisCommand<GeoResults<GeoLocation<ByteBuffer>>> cmd;
            if (args.getFlags().contains(GeoRadiusCommandArgs.Flag.WITHCOORD)) {
                cmd = new RedisCommand<>("GEORADIUSBYMEMBER_RO", postitionDecoder);
                params.add("WITHCOORD");
            } else {
                MultiDecoder<GeoResults<GeoLocation<ByteBuffer>>> distanceDecoder = new ListMultiDecoder2(new ByteBufferGeoResultsDecoder(command.getDistance().getMetric()), new GeoDistanceDecoder());
                cmd = new RedisCommand<>("GEORADIUSBYMEMBER_RO", distanceDecoder);
                params.add("WITHDIST");
            }
            
            if (args.getLimit() != null) {
                params.add("COUNT");
                params.add(args.getLimit());
            }
            if (args.getSortDirection() != null) {
                params.add(args.getSortDirection().name());
            }
            
            Mono<GeoResults<GeoLocation<ByteBuffer>>> m = read(keyBuf, ByteArrayCodec.INSTANCE, cmd, params.toArray());
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v.getContent())));
        });
    }

    private String convert(double longitude) {
        return BigDecimal.valueOf(longitude).toPlainString();
    }

    private ByteBuf encode(Object value) {
        return executorService.encode(ByteArrayCodec.INSTANCE, value);
    }

    @Override
    public Flux<CommandResponse<GeoSearchCommand, Flux<GeoResult<GeoLocation<ByteBuffer>>>>> geoSearch(Publisher<GeoSearchCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getArgs(), "Args must not be null!");
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getShape(), "Shape must not be null!");
            Assert.notNull(command.getReference(), "Reference must not be null!");

            List<Object> commandParams = new ArrayList<>();
            byte[] keyBuf = toByteArray(command.getKey());
            commandParams.add(keyBuf);

            if (command.getReference() instanceof GeoReference.GeoCoordinateReference) {
                GeoReference.GeoCoordinateReference ref = (GeoReference.GeoCoordinateReference) command.getReference();
                commandParams.add("FROMLONLAT");
                commandParams.add(convert(ref.getLongitude()));
                commandParams.add(convert(ref.getLatitude()));
            } else if (command.getReference() instanceof GeoReference.GeoMemberReference) {
                GeoReference.GeoMemberReference ref = (GeoReference.GeoMemberReference) command.getReference();
                commandParams.add("FROMMEMBER");
                commandParams.add(encode(ref.getMember()));
            }

            if (command.getShape() instanceof RadiusShape) {
                commandParams.add("BYRADIUS");
                RadiusShape shape = (RadiusShape) command.getShape();
                commandParams.add(shape.getRadius().getValue());
                commandParams.add(convert(shape.getMetric()).getAbbreviation());
            } else if (command.getShape() instanceof BoxShape) {
                BoxShape shape = (BoxShape) command.getShape();
                commandParams.add("BYBOX");
                commandParams.add(shape.getBoundingBox().getWidth().getValue());
                commandParams.add(shape.getBoundingBox().getHeight().getValue());
                commandParams.add(convert(shape.getMetric()).getAbbreviation());
            }

            RedisGeoCommands.GeoSearchCommandArgs args = command.getArgs()
                                                            .orElse(RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs());
            if (args.hasSortDirection()) {
                commandParams.add(args.getSortDirection());
            }
            if (args.getLimit() != null) {
                commandParams.add("COUNT");
                commandParams.add(args.getLimit());
                if (args.hasAnyLimit()) {
                    commandParams.add("ANY");
                }
            }
            RedisCommand<GeoResults<GeoLocation<ByteBuffer>>> cmd;
            if (args.getFlags().contains(GeoRadiusCommandArgs.Flag.WITHCOORD)) {
                cmd = new RedisCommand<>("GEOSEARCH", postitionDecoder);
                commandParams.add("WITHCOORD");
            } else {
                MultiDecoder<GeoResults<GeoLocation<ByteBuffer>>> distanceDecoder = new ListMultiDecoder2(new ByteBufferGeoResultsDecoder(command.getShape().getMetric()), new GeoDistanceDecoder());
                cmd = new RedisCommand<>("GEOSEARCH", distanceDecoder);
                commandParams.add("WITHDIST");
            }

            Mono<GeoResults<GeoLocation<ByteBuffer>>> m = read(keyBuf, ByteArrayCodec.INSTANCE, cmd, commandParams.toArray());
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v.getContent())));
        });
    }

    @Override
    public Flux<NumericResponse<GeoSearchStoreCommand, Long>> geoSearchStore(Publisher<GeoSearchStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getArgs(), "Args must not be null!");
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getDestKey(), "DestKey must not be null!");
            Assert.notNull(command.getShape(), "Shape must not be null!");
            Assert.notNull(command.getReference(), "Reference must not be null!");

            List<Object> commandParams = new ArrayList<>();
            byte[] destKeyBuf = toByteArray(command.getDestKey());
            commandParams.add(destKeyBuf);
            byte[] keyBuf = toByteArray(command.getKey());
            commandParams.add(keyBuf);

            if (command.getReference() instanceof GeoReference.GeoCoordinateReference) {
                GeoReference.GeoCoordinateReference ref = (GeoReference.GeoCoordinateReference) command.getReference();
                commandParams.add("FROMLONLAT");
                commandParams.add(convert(ref.getLongitude()));
                commandParams.add(convert(ref.getLatitude()));
            } else if (command.getReference() instanceof GeoReference.GeoMemberReference) {
                GeoReference.GeoMemberReference ref = (GeoReference.GeoMemberReference) command.getReference();
                commandParams.add("FROMMEMBER");
                commandParams.add(encode(ref.getMember()));
            }

            if (command.getShape() instanceof RadiusShape) {
                RadiusShape shape = (RadiusShape) command.getShape();
                commandParams.add("BYRADIUS");
                commandParams.add(shape.getRadius().getValue());
                commandParams.add(convert(shape.getMetric()).getAbbreviation());
            } else if (command.getShape() instanceof BoxShape) {
                BoxShape shape = (BoxShape) command.getShape();
                commandParams.add("BYBOX");
                commandParams.add(shape.getBoundingBox().getWidth().getValue());
                commandParams.add(shape.getBoundingBox().getHeight().getValue());
                commandParams.add(convert(shape.getMetric()).getAbbreviation());
            }

            RedisGeoCommands.GeoSearchStoreCommandArgs args = command.getArgs()
                                                                .orElse(RedisGeoCommands.GeoSearchStoreCommandArgs.newGeoSearchStoreArgs());
            if (args.hasSortDirection()) {
                commandParams.add(args.getSortDirection());
            }
            if (args.getLimit() != null) {
                commandParams.add("COUNT");
                commandParams.add(args.getLimit());
                if (args.hasAnyLimit()) {
                    commandParams.add("ANY");
                }
            }
            if (args.isStoreDistance()) {
                commandParams.add("STOREDIST");
            }

            Mono<Long> m = write(keyBuf, LongCodec.INSTANCE, RedisCommands.GEOSEARCHSTORE_STORE, commandParams.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    private Metric convert(Metric metric) {
        if (metric == Metrics.NEUTRAL) {
            return RedisGeoCommands.DistanceUnit.METERS;
        }
        return metric;
    }

}
