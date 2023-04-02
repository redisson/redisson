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

import java.math.BigDecimal;
import java.util.*;

import org.redisson.api.GeoEntry;
import org.redisson.api.GeoOrder;
import org.redisson.api.GeoPosition;
import org.redisson.api.GeoUnit;
import org.redisson.api.RFuture;
import org.redisson.api.RGeo;
import org.redisson.api.RedissonClient;
import org.redisson.api.geo.GeoSearchArgs;
import org.redisson.api.geo.GeoSearchParams;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.CodecDecoder;
import org.redisson.client.protocol.decoder.GeoDistanceDecoder;
import org.redisson.client.protocol.decoder.GeoPositionDecoder;
import org.redisson.client.protocol.decoder.GeoPositionMapDecoder;
import org.redisson.client.protocol.decoder.ListMultiDecoder2;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder2;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;

/**
 * Geospatial items holder
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonGeo<V> extends RedissonScoredSortedSet<V> implements RGeo<V> {

    private static final MultiDecoder<Map<Object, Object>> POSTITION_DECODER = new ListMultiDecoder2(
            new ObjectMapReplayDecoder2(),
            new CodecDecoder(),
            new GeoPositionDecoder());
    
    private static final MultiDecoder<Map<Object, Object>> DISTANCE_DECODER = new ListMultiDecoder2(
            new ObjectMapReplayDecoder2(),
            new GeoDistanceDecoder());
    
    private static final RedisCommand<Map<Object, Object>> GEORADIUS_RO_DISTANCE = new RedisCommand<Map<Object, Object>>(
            "GEORADIUS_RO", DISTANCE_DECODER);
    private static final RedisCommand<Map<Object, Object>> GEOSEARCH_DISTANCE = new RedisCommand<Map<Object, Object>>(
            "GEOSEARCH", DISTANCE_DECODER);
    private static final RedisCommand<Map<Object, Object>> GEOSEARCH_POS = new RedisCommand<Map<Object, Object>>(
            "GEOSEARCH", POSTITION_DECODER);
    private static final RedisCommand<Map<Object, Object>> GEORADIUS_RO_POS = new RedisCommand<Map<Object, Object>>(
            "GEORADIUS_RO", POSTITION_DECODER);
    private static final RedisCommand<Map<Object, Object>> GEORADIUSBYMEMBER_RO_DISTANCE = new RedisCommand<Map<Object, Object>>(
            "GEORADIUSBYMEMBER_RO", DISTANCE_DECODER);
    private static final RedisCommand<Map<Object, Object>> GEORADIUSBYMEMBER_RO_POS = new RedisCommand<Map<Object, Object>>(
            "GEORADIUSBYMEMBER_RO", POSTITION_DECODER);

    public RedissonGeo(CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(connectionManager, name, redisson);
    }

    public RedissonGeo(Codec codec, CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(codec, connectionManager, name, redisson);
    }

    @Override
    public RFuture<Long> addAsync(double longitude, double latitude, V member) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GEOADD, getRawName(), convert(longitude),
                convert(latitude), encode(member));
    }

    private String convert(double longitude) {
        return BigDecimal.valueOf(longitude).toPlainString();
    }

    @Override
    public long add(double longitude, double latitude, V member) {
        return get(addAsync(longitude, latitude, member));
    }

    @Override
    public long add(GeoEntry... entries) {
        return get(addAsync(entries));
    }

    @Override
    public RFuture<Long> addAsync(GeoEntry... entries) {
        return addAsync("", entries);
    }

    private RFuture<Long> addAsync(String subCommand, GeoEntry... entries) {
        List<Object> params = new ArrayList<>(entries.length + 2);
        params.add(getRawName());
        if (!subCommand.isEmpty()) {
            params.add(subCommand);
        }
        for (GeoEntry entry : entries) {
            params.add(entry.getLongitude());
            params.add(entry.getLatitude());
            encode(params, entry.getMember());
        }
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.GEOADD, params.toArray());
    }

    @Override
    public Boolean addIfExists(double longitude, double latitude, V member) {
        return get(addIfExistsAsync(longitude, latitude, member));
    }

    @Override
    public long addIfExists(GeoEntry... entries) {
        return get(addIfExistsAsync(entries));
    }

    @Override
    public RFuture<Boolean> addIfExistsAsync(double longitude, double latitude, V member) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
            "local value = redis.call('geopos', KEYS[1], ARGV[3]); "
                + "if value[1] ~= false then "
                    + "redis.call('geoadd', KEYS[1], ARGV[1], ARGV[2], ARGV[3]); "
                    + "return 1; "
                + "end; "
                + "return 0; ",
                Collections.singletonList(getRawName()),
                convert(longitude), convert(latitude), encode(member));
    }

    @Override
    public RFuture<Long> addIfExistsAsync(GeoEntry... entries) {
        return addAsync("XX", entries);
    }

    @Override
    public boolean tryAdd(double longitude, double latitude, V member) {
        return get(tryAddAsync(longitude, latitude, member));
    }

    @Override
    public long tryAdd(GeoEntry... entries) {
        return get(tryAddAsync(entries));
    }

    @Override
    public RFuture<Boolean> tryAddAsync(double longitude, double latitude, V member) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GEOADD_BOOLEAN, getRawName(), "NX", convert(longitude),
                convert(latitude), encode(member));
    }

    @Override
    public RFuture<Long> tryAddAsync(GeoEntry... entries) {
        return addAsync("NX", entries);
    }

    @Override
    public Double dist(V firstMember, V secondMember, GeoUnit geoUnit) {
        return get(distAsync(firstMember, secondMember, geoUnit));
    }

    @Override
    public RFuture<Double> distAsync(V firstMember, V secondMember, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.GEODIST, getRawName(),
                encode(firstMember), encode(secondMember), geoUnit);
    }

    @Override
    public Map<V, String> hash(V... members) {
        return get(hashAsync(members));
    }

    @Override
    public RFuture<Map<V, String>> hashAsync(V... members) {
        List<Object> params = new ArrayList<>(members.length + 1);
        params.add(getRawName());
        for (Object member : members) {
            encode(params, member);
        }
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEOHASH",
                new MapGetAllDecoder((List<Object>) Arrays.asList(members), 0));
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, command, params.toArray());
    }

    @Override
    public Map<V, GeoPosition> pos(V... members) {
        return get(posAsync(members));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> posAsync(V... members) {
        List<Object> params = new ArrayList<>(members.length + 1);
        params.add(getRawName());
        for (Object member : members) {
            encode(params, member);
        }

        MultiDecoder<Map<Object, Object>> decoder = new ListMultiDecoder2(
                new GeoPositionMapDecoder((List<Object>) Arrays.asList(members)),
                new GeoPositionDecoder());
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEOPOS", decoder);
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, command, params.toArray());
    }

    @Override
    public List<V> search(GeoSearchArgs args) {
        return get(searchAsync(args));
    }

    @Override
    public RFuture<List<V>> searchAsync(GeoSearchArgs args) {
        GeoSearchParams params = (GeoSearchParams) args;

        List<Object> commandParams = new ArrayList<>();
        commandParams.add(getRawName());
        RedisCommand command = null;
        if (params.getLatitude() != null
                && params.getLongitude() != null) {
            command = RedisCommands.GEORADIUS_RO;
            if (params.getHeight() != null) {
                command = RedisCommands.GEOSEARCH;
                commandParams.add("FROMLONLAT");
            }
            commandParams.add(convert(params.getLongitude()));
            commandParams.add(convert(params.getLatitude()));
        }
        if (params.getMember() != null) {
            command = RedisCommands.GEORADIUSBYMEMBER_RO;
            if (params.getHeight() != null) {
                command = RedisCommands.GEOSEARCH;
                commandParams.add("FROMMEMBER");
            }
            commandParams.add(encode(params.getMember()));
        }
        if (params.getRadius() != null
            && params.getUnit() != null) {
            commandParams.add(params.getRadius());
            commandParams.add(params.getUnit());
        }
        if (params.getHeight() != null
            && params.getUnit() != null) {
            commandParams.add("BYBOX");
            commandParams.add(params.getWidth());
            commandParams.add(params.getHeight());
            commandParams.add(params.getUnit());

            if (params.getOrder() != null) {
                commandParams.add(params.getOrder());
            }
        }
        if (params.getCount() != null) {
            commandParams.add("COUNT");
            commandParams.add(params.getCount());
            if (params.isCountAny()) {
                commandParams.add("ANY");
            }
        }
        if (params.getHeight() == null
                && params.getOrder() != null) {
            commandParams.add(params.getOrder());
        }

        return commandExecutor.readAsync(getRawName(), codec, command, commandParams.toArray());
    }

    @Override
    public Map<V, Double> searchWithDistance(GeoSearchArgs args) {
        return get(searchWithDistanceAsync(args));
    }

    @Override
    public RFuture<Map<V, Double>> searchWithDistanceAsync(GeoSearchArgs args) {
        GeoSearchParams params = (GeoSearchParams) args;

        List<Object> commandParams = new ArrayList<>();
        commandParams.add(getRawName());
        RedisCommand command = null;
        if (params.getLatitude() != null
                && params.getLongitude() != null) {
            command = GEORADIUS_RO_DISTANCE;
            if (params.getHeight() != null) {
                command = GEOSEARCH_DISTANCE;
                commandParams.add("FROMLONLAT");
            }
            commandParams.add(convert(params.getLongitude()));
            commandParams.add(convert(params.getLatitude()));
        }
        if (params.getMember() != null) {
            command = GEORADIUSBYMEMBER_RO_DISTANCE;
            if (params.getHeight() != null) {
                command = GEOSEARCH_DISTANCE;
                commandParams.add("FROMMEMBER");
            }
            commandParams.add(encode(params.getMember()));
        }
        if (params.getRadius() != null
            && params.getUnit() != null) {
            commandParams.add(params.getRadius());
            commandParams.add(params.getUnit());
        }
        if (params.getHeight() != null
            && params.getUnit() != null) {
            commandParams.add("BYBOX");
            commandParams.add(params.getWidth());
            commandParams.add(params.getHeight());
            commandParams.add(params.getUnit());

            if (params.getOrder() != null) {
                commandParams.add(params.getOrder());
            }
        }
        if (params.getHeight() == null) {
            commandParams.add("WITHDIST");
        }
        if (params.getCount() != null) {
            commandParams.add("COUNT");
            commandParams.add(params.getCount());
            if (params.isCountAny()) {
                commandParams.add("ANY");
            }
        }
        if (params.getHeight() == null
                && params.getOrder() != null) {
            commandParams.add(params.getOrder());
        }
        if (params.getHeight() != null) {
            commandParams.add("WITHDIST");
        }

        return commandExecutor.readAsync(getRawName(), codec, command, commandParams.toArray());
    }

    @Override
    public Map<V, GeoPosition> searchWithPosition(GeoSearchArgs args) {
        return get(searchWithPositionAsync(args));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> searchWithPositionAsync(GeoSearchArgs args) {
        GeoSearchParams params = (GeoSearchParams) args;

        List<Object> commandParams = new ArrayList<>();
        commandParams.add(getRawName());
        RedisCommand command = null;
        if (params.getLatitude() != null
                && params.getLongitude() != null) {
            command = GEORADIUS_RO_POS;
            if (params.getHeight() != null) {
                command = GEOSEARCH_POS;
                commandParams.add("FROMLONLAT");
            }
            commandParams.add(convert(params.getLongitude()));
            commandParams.add(convert(params.getLatitude()));
        }
        if (params.getMember() != null) {
            command = GEORADIUSBYMEMBER_RO_POS;
            if (params.getHeight() != null) {
                command = GEOSEARCH_POS;
                commandParams.add("FROMMEMBER");
            }
            commandParams.add(encode(params.getMember()));
        }
        if (params.getRadius() != null
                && params.getUnit() != null) {
            commandParams.add(params.getRadius());
            commandParams.add(params.getUnit());
        }
        if (params.getHeight() != null
            && params.getUnit() != null) {
            commandParams.add("BYBOX");
            commandParams.add(params.getWidth());
            commandParams.add(params.getHeight());
            commandParams.add(params.getUnit());

            if (params.getOrder() != null) {
                commandParams.add(params.getOrder());
            }
        }
        if (params.getHeight() == null) {
            commandParams.add("WITHCOORD");
        }
        if (params.getCount() != null) {
            commandParams.add("COUNT");
            commandParams.add(params.getCount());
            if (params.isCountAny()) {
                commandParams.add("ANY");
            }
        }
        if (params.getHeight() == null
                && params.getOrder() != null) {
            commandParams.add(params.getOrder());
        }
        if (params.getHeight() != null) {
            commandParams.add("WITHCOORD");
        }

        return commandExecutor.readAsync(getRawName(), codec, command, commandParams.toArray());
    }

    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit));
    }

    @Override
    public RFuture<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GEORADIUS_RO, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit);
    }

    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit, count));
    }

    @Override
    public RFuture<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GEORADIUS_RO, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "COUNT", count);
    }

    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit, geoOrder));
    }

    @Override
    public RFuture<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GEORADIUS_RO, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, geoOrder);
    }

    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder,
            int count) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GEORADIUS_RO, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "COUNT", count, geoOrder);
    }

    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius,
            GeoUnit geoUnit) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUS_RO_DISTANCE, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "WITHDIST");
    }

    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit,
            int count) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, count));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius,
            GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUS_RO_DISTANCE, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "WITHDIST", "COUNT", count);
    }

    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, geoOrder));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius,
            GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUS_RO_DISTANCE, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "WITHDIST", geoOrder);
    }

    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder, int count) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius,
            GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUS_RO_DISTANCE, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "WITHDIST", "COUNT", count, geoOrder);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius,
            GeoUnit geoUnit) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUS_RO_POS, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "WITHCOORD");
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit,
            int count) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit, count));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius,
            GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUS_RO_POS, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "WITHCOORD", "COUNT", count);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit, geoOrder));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius,
            GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUS_RO_POS, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "WITHCOORD", geoOrder);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder, int count) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius,
            GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUS_RO_POS, getRawName(), convert(longitude),
                convert(latitude), radius, geoUnit, "WITHCOORD", "COUNT", count, geoOrder);
    }

    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit) {
        return get(radiusAsync(member, radius, geoUnit));
    }

    @Override
    public RFuture<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_RO, getRawName(),
                encode(member), radius, geoUnit);
    }

    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit, int count) {
        return get(radiusAsync(member, radius, geoUnit, count));
    }

    @Override
    public RFuture<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_RO, getRawName(),
                encode(member), radius, geoUnit, "COUNT", count);
    }

    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusAsync(member, radius, geoUnit, geoOrder));
    }

    @Override
    public RFuture<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_RO, getRawName(),
                encode(member), radius, geoUnit, geoOrder);
    }

    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusAsync(member, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_RO, getRawName(),
                encode(member), radius, geoUnit, "COUNT", count, geoOrder);
    }

    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUSBYMEMBER_RO_DISTANCE, getRawName(), encode(member),
                radius, geoUnit, "WITHDIST");
    }

    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit, int count) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit, count));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUSBYMEMBER_RO_DISTANCE, getRawName(), encode(member),
                radius, geoUnit, "WITHDIST", "COUNT", count);
    }

    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit, geoOrder));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUSBYMEMBER_RO_DISTANCE, getRawName(), encode(member),
                radius, geoUnit, "WITHDIST", geoOrder);
    }

    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder,
            int count) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUSBYMEMBER_RO_DISTANCE, getRawName(), encode(member),
                radius, geoUnit, "WITHDIST", "COUNT", count, geoOrder);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit) {
        return get(radiusWithPositionAsync(member, radius, geoUnit));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUSBYMEMBER_RO_POS, getRawName(), encode(member), radius,
                geoUnit, "WITHCOORD");
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit, int count) {
        return get(radiusWithPositionAsync(member, radius, geoUnit, count));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUSBYMEMBER_RO_POS, getRawName(), encode(member), radius,
                geoUnit, "WITHCOORD", "COUNT", count);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusWithPositionAsync(member, radius, geoUnit, geoOrder));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUSBYMEMBER_RO_POS, getRawName(), encode(member), radius,
                geoUnit, "WITHCOORD", geoOrder);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder,
            int count) {
        return get(radiusWithPositionAsync(member, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getRawName(), codec, GEORADIUSBYMEMBER_RO_POS, getRawName(), encode(member), radius,
                geoUnit, "WITHCOORD", "COUNT", count, geoOrder);
    }

    @Override
    public long radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit));
    }

    @Override
    public RFuture<Long> radiusStoreToAsync(String destName, double longitude, double latitude, double radius,
            GeoUnit geoUnit) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getRawName(),
                convert(longitude), convert(latitude), radius, geoUnit, "STORE", destName);
    }

    @Override
    public long storeSearchTo(String destName, GeoSearchArgs args) {
        return get(storeSearchToAsync(destName, args));
    }

    @Override
    public RFuture<Long> storeSearchToAsync(String destName, GeoSearchArgs args) {
        GeoSearchParams params = (GeoSearchParams) args;

        List<Object> commandParams = new ArrayList<>();
        if (params.getHeight() != null) {
            commandParams.add(destName);
        }
        commandParams.add(getRawName());
        RedisCommand command = null;
        if (params.getLatitude() != null
                && params.getLongitude() != null) {
            command = RedisCommands.GEORADIUS_STORE;
            if (params.getHeight() != null) {
                command = RedisCommands.GEOSEARCHSTORE_STORE;
                commandParams.add("FROMLONLAT");
            }
            commandParams.add(convert(params.getLongitude()));
            commandParams.add(convert(params.getLatitude()));
        }
        if (params.getMember() != null) {
            command = RedisCommands.GEORADIUSBYMEMBER_STORE;
            if (params.getHeight() != null) {
                command = RedisCommands.GEOSEARCHSTORE_STORE;
                commandParams.add("FROMMEMBER");
            }
            commandParams.add(encode(params.getMember()));
        }
        if (params.getRadius() != null
                && params.getUnit() != null) {
            commandParams.add(params.getRadius());
            commandParams.add(params.getUnit());
        }
        if (params.getHeight() != null
            && params.getUnit() != null) {
            commandParams.add("BYBOX");
            commandParams.add(params.getWidth());
            commandParams.add(params.getHeight());
            commandParams.add(params.getUnit());

            if (params.getOrder() != null) {
                commandParams.add(params.getOrder());
            }
        }
        if (params.getCount() != null) {
            commandParams.add("COUNT");
            commandParams.add(params.getCount());
            if (params.isCountAny()) {
                commandParams.add("ANY");
            }
        }
        if (params.getHeight() == null
                && params.getOrder() != null) {
            commandParams.add(params.getOrder());
        }
        if (params.getHeight() == null) {
            commandParams.add("STORE");
            commandParams.add(destName);
        }

        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, command, commandParams.toArray());
    }

    @Override
    public long radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit,
            int count) {
        return get(radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit, count));
    }

    @Override
    public RFuture<Long> radiusStoreToAsync(String destName, double longitude, double latitude, double radius,
            GeoUnit geoUnit, int count) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getRawName(),
                convert(longitude), convert(latitude), radius, geoUnit, "COUNT", count, "STORE", destName);
    }

    @Override
    public long radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder, int count) {
        return get(radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<Long> radiusStoreToAsync(String destName, double longitude, double latitude, double radius,
            GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getRawName(),
                convert(longitude), convert(latitude), radius, geoUnit, geoOrder, "COUNT", count, "STORE", destName);
    }

    @Override
    public long radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit) {
        return get(radiusStoreToAsync(destName, member, radius, geoUnit));
    }

    @Override
    public RFuture<Long> radiusStoreToAsync(String destName, V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getRawName(),
                encode(member), radius, geoUnit, "STORE", destName);
    }

    @Override
    public long radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit, int count) {
        return get(radiusStoreToAsync(destName, member, radius, geoUnit, count));
    }

    @Override
    public RFuture<Long> radiusStoreToAsync(String destName, V member, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getRawName(),
                encode(member), radius, geoUnit, "COUNT", count, "STORE", destName);
    }

    @Override
    public long radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusStoreToAsync(destName, member, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<Long> radiusStoreToAsync(String destName, V member, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder, int count) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getRawName(),
                encode(member), radius, geoUnit, geoOrder, "COUNT", count, "STORE", destName);
    }

    @Override
    public long storeSortedSearchTo(String destName, GeoSearchArgs args) {
        return get(storeSortedSearchToAsync(destName, args));
    }

    @Override
    public RFuture<Long> storeSortedSearchToAsync(String destName, GeoSearchArgs args) {
        GeoSearchParams params = (GeoSearchParams) args;

        List<Object> commandParams = new ArrayList<>();
        if (params.getHeight() != null) {
            commandParams.add(destName);
        }
        commandParams.add(getRawName());
        RedisCommand command = null;
        if (params.getLatitude() != null
                && params.getLongitude() != null) {
            command = RedisCommands.GEORADIUS_STORE;
            if (params.getHeight() != null) {
                command = RedisCommands.GEOSEARCHSTORE_STORE;
                commandParams.add("FROMLONLAT");
            }
            commandParams.add(convert(params.getLongitude()));
            commandParams.add(convert(params.getLatitude()));
        }
        if (params.getMember() != null) {
            command = RedisCommands.GEORADIUSBYMEMBER_STORE;
            if (params.getHeight() != null) {
                command = RedisCommands.GEOSEARCHSTORE_STORE;
                commandParams.add("FROMMEMBER");
            }
            commandParams.add(encode(params.getMember()));
        }
        if (params.getRadius() != null
                && params.getUnit() != null) {
            commandParams.add(params.getRadius());
            commandParams.add(params.getUnit());
        }
        if (params.getHeight() != null
            && params.getUnit() != null) {
            commandParams.add("BYBOX");
            commandParams.add(params.getWidth());
            commandParams.add(params.getHeight());
            commandParams.add(params.getUnit());

            if (params.getOrder() != null) {
                commandParams.add(params.getOrder());
            }
        }
        if (params.getCount() != null) {
            commandParams.add("COUNT");
            commandParams.add(params.getCount());
            if (params.isCountAny()) {
                commandParams.add("ANY");
            }
        }
        if (params.getHeight() == null
                && params.getOrder() != null) {
            commandParams.add(params.getOrder());
        }
        commandParams.add("STOREDIST");
        if (params.getHeight() == null) {
            commandParams.add(destName);
        }

        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, command, commandParams.toArray());
    }

    @Override
    public RFuture<Long> radiusStoreSortedToAsync(String destName, double longitude, double latitude, double radius,
            GeoUnit geoUnit) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getRawName(),
                convert(longitude), convert(latitude), radius, geoUnit, "STOREDIST", destName);
    }

    @Override
    public RFuture<Long> radiusStoreSortedToAsync(String destName, double longitude, double latitude, double radius,
            GeoUnit geoUnit, int count) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getRawName(),
                convert(longitude), convert(latitude), radius, geoUnit, "COUNT", count, "STOREDIST", destName);
    }

    @Override
    public RFuture<Long> radiusStoreSortedToAsync(String destName, double longitude, double latitude, double radius,
            GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getRawName(),
                convert(longitude), convert(latitude), radius, geoUnit, geoOrder, "COUNT", count, "STOREDIST", destName);
    }

    @Override
    public RFuture<Long> radiusStoreSortedToAsync(String destName, V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getRawName(),
                encode(member), radius, geoUnit, "STOREDIST", destName);
    }

    @Override
    public RFuture<Long> radiusStoreSortedToAsync(String destName, V member, double radius, GeoUnit geoUnit,
            int count) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getRawName(),
                encode(member), radius, geoUnit, "COUNT", count, "STOREDIST", destName);
    }

    @Override
    public RFuture<Long> radiusStoreSortedToAsync(String destName, V member, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder, int count) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getRawName(),
                encode(member), radius, geoUnit, geoOrder, "COUNT", count, "STOREDIST", destName);
    }

    @Override
    public long radiusStoreSortedTo(String destName, double longitude, double latitude, double radius,
            GeoUnit geoUnit) {
        return get(radiusStoreSortedToAsync(destName, longitude, latitude, radius, geoUnit));
    }

    @Override
    public long radiusStoreSortedTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit,
            int count) {
        return get(radiusStoreSortedToAsync(destName, longitude, latitude, radius, geoUnit, count));
    }

    @Override
    public long radiusStoreSortedTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit,
            GeoOrder geoOrder, int count) {
        return get(radiusStoreSortedToAsync(destName, longitude, latitude, radius, geoUnit, geoOrder, count));
    }

    @Override
    public long radiusStoreSortedTo(String destName, V member, double radius, GeoUnit geoUnit) {
        return get(radiusStoreSortedToAsync(destName, member, radius, geoUnit));
    }

    @Override
    public long radiusStoreSortedTo(String destName, V member, double radius, GeoUnit geoUnit, int count) {
        return get(radiusStoreSortedToAsync(destName, member, radius, geoUnit));
    }

    @Override
    public long radiusStoreSortedTo(String destName, V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder,
            int count) {
        return get(radiusStoreSortedToAsync(destName, member, radius, geoUnit));
    }

}
