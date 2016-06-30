/**
 * Copyright 2016 Nikita Koksharov
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.GeoEntryCodec;
import org.redisson.client.codec.ScoredCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.GeoDistanceDecoder;
import org.redisson.client.protocol.decoder.GeoMapReplayDecoder;
import org.redisson.client.protocol.decoder.GeoPositionDecoder;
import org.redisson.client.protocol.decoder.GeoPositionMapDecoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.decoder.NestedMultiDecoder;
import org.redisson.client.protocol.decoder.FlatNestedMultiDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.core.GeoEntry;
import org.redisson.core.GeoPosition;
import org.redisson.core.GeoUnit;
import org.redisson.core.RGeo;

import io.netty.util.concurrent.Future;

public class RedissonGeo<V> extends RedissonExpirable implements RGeo<V> {

    MultiDecoder<Map<Object, Object>> postitionDecoder;
    MultiDecoder<Map<Object, Object>> distanceDecoder;
    
    public RedissonGeo(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
        postitionDecoder = new NestedMultiDecoder(new GeoPositionDecoder(), new GeoDistanceDecoder(codec), new GeoMapReplayDecoder(), true);
        distanceDecoder = new FlatNestedMultiDecoder(new GeoDistanceDecoder(codec), new GeoMapReplayDecoder(), true);
    }
    
    public RedissonGeo(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
        postitionDecoder = new NestedMultiDecoder(new GeoPositionDecoder(), new GeoDistanceDecoder(codec), new GeoMapReplayDecoder(), true);
        distanceDecoder = new FlatNestedMultiDecoder(new GeoDistanceDecoder(codec), new GeoMapReplayDecoder(), true);
    }

    @Override
    public Future<Long> addAsync(double longitude, double latitude, V member) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.GEOADD, getName(), convert(longitude), convert(latitude), member);
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
    public Future<Long> addAsync(GeoEntry... entries) {
        List<Object> params = new ArrayList<Object>(entries.length + 1);
        params.add(getName());
        for (GeoEntry entry : entries) {
            params.add(entry.getLongitude());
            params.add(entry.getLatitude());
            params.add(entry.getMember());
        }
        return commandExecutor.writeAsync(getName(), new GeoEntryCodec(codec), RedisCommands.GEOADD_ENTRIES, params.toArray());
    }

    @Override
    public Double dist(V firstMember, V secondMember, GeoUnit geoUnit) {
        return get(distAsync(firstMember, secondMember, geoUnit));
    }
    
    @Override
    public Future<Double> distAsync(V firstMember, V secondMember, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), new ScoredCodec(codec), RedisCommands.GEODIST, getName(), firstMember, secondMember, geoUnit);
    }
    
    @Override
    public Map<V, String> hash(V... members) {
        return get(hashAsync(members));
    }
    
    @Override
    public Future<Map<V, String>> hashAsync(V... members) {
        List<Object> params = new ArrayList<Object>(members.length + 1);
        params.add(getName());
        params.addAll(Arrays.asList(members));
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEOHASH", new MapGetAllDecoder(params, 1), 2, ValueType.OBJECTS);
        return commandExecutor.readAsync(getName(), new ScoredCodec(codec), command, params.toArray());
    }
    
    @Override
    public Map<V, GeoPosition> pos(V... members) {
        return get(posAsync(members));
    }
    
    @Override
    public Future<Map<V, GeoPosition>> posAsync(V... members) {
        List<Object> params = new ArrayList<Object>(members.length + 1);
        params.add(getName());
        params.addAll(Arrays.asList(members));
        
        MultiDecoder<Map<Object, Object>> decoder = new NestedMultiDecoder(new GeoPositionDecoder(), new GeoPositionMapDecoder(params), true);
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEOPOS", decoder, 2, ValueType.OBJECTS);
        return commandExecutor.readAsync(getName(), new ScoredCodec(codec), command, params.toArray());
    }
    
    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit));
    }
    
    @Override
    public Future<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUS, getName(), convert(longitude), convert(latitude), radius, geoUnit);
    }
    
    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit));
    }
    
    @Override
    public Future<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEORADIUS", distanceDecoder);
        return commandExecutor.readAsync(getName(), codec, command, getName(), convert(longitude), convert(latitude), radius, geoUnit, "WITHDIST");
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit));
    }
    
    @Override
    public Future<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEORADIUS", postitionDecoder);
        return commandExecutor.readAsync(getName(), codec, command, getName(), convert(longitude), convert(latitude), radius, geoUnit, "WITHCOORD");
    }

    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit) {
        return get(radiusAsync(member, radius, geoUnit));
    }
    
    @Override
    public Future<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUSBYMEMBER, getName(), member, radius, geoUnit);
    }
    
    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit));
    }
    
    @Override
    public Future<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit) {
        RedisCommand command = new RedisCommand("GEORADIUSBYMEMBER", distanceDecoder, 2);
        return commandExecutor.readAsync(getName(), codec, command, getName(), member, radius, geoUnit, "WITHDIST");
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit) {
        return get(radiusWithPositionAsync(member, radius, geoUnit));
    }
    
    @Override
    public Future<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit) {
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEORADIUSBYMEMBER", postitionDecoder, 2);
        return commandExecutor.readAsync(getName(), codec, command, getName(), member, radius, geoUnit, "WITHCOORD");
    }
}
