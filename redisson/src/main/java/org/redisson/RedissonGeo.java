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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.redisson.api.GeoEntry;
import org.redisson.api.GeoOrder;
import org.redisson.api.GeoPosition;
import org.redisson.api.GeoUnit;
import org.redisson.api.RFuture;
import org.redisson.api.RGeo;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.CodecDecoder;
import org.redisson.client.protocol.decoder.GeoDistanceDecoder;
import org.redisson.client.protocol.decoder.GeoMapReplayDecoder;
import org.redisson.client.protocol.decoder.GeoPositionDecoder;
import org.redisson.client.protocol.decoder.GeoPositionMapDecoder;
import org.redisson.client.protocol.decoder.ListMultiDecoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
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

    private static final MultiDecoder<Map<Object, Object>> postitionDecoder = new ListMultiDecoder(new CodecDecoder(), new GeoPositionDecoder(), new ObjectListReplayDecoder(ListMultiDecoder.RESET), new GeoMapReplayDecoder());
    private static final MultiDecoder<Map<Object, Object>> distanceDecoder = new ListMultiDecoder(new GeoDistanceDecoder(), new GeoMapReplayDecoder());
    private static final RedisCommand<Map<Object, Object>> GEORADIUS_RO_DISTANCE = new RedisCommand<Map<Object, Object>>("GEORADIUS_RO", distanceDecoder);
    private static final RedisCommand<Map<Object, Object>> GEORADIUS_RO_POS = new RedisCommand<Map<Object, Object>>("GEORADIUS_RO", postitionDecoder);
    private static final RedisCommand<Map<Object, Object>> GEORADIUSBYMEMBER_RO_DISTANCE = new RedisCommand<Map<Object, Object>>("GEORADIUSBYMEMBER_RO", distanceDecoder);
    private static final RedisCommand<Map<Object, Object>> GEORADIUSBYMEMBER_RO_POS = new RedisCommand<Map<Object, Object>>("GEORADIUSBYMEMBER_RO", postitionDecoder);
    
    public RedissonGeo(CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(connectionManager, name, redisson);
    }
    
    public RedissonGeo(Codec codec, CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(codec, connectionManager, name, redisson);
    }

    @Override
    public RFuture<Long> addAsync(double longitude, double latitude, V member) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.GEOADD, getName(), convert(longitude), convert(latitude), encode(member));
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
        List<Object> params = new ArrayList<Object>(entries.length + 1);
        params.add(getName());
        for (GeoEntry entry : entries) {
            params.add(entry.getLongitude());
            params.add(entry.getLatitude());
            params.add(encode(entry.getMember()));
        }
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.GEOADD, params.toArray());
    }

    @Override
    public Double dist(V firstMember, V secondMember, GeoUnit geoUnit) {
        return get(distAsync(firstMember, secondMember, geoUnit));
    }
    
    @Override
    public RFuture<Double> distAsync(V firstMember, V secondMember, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.GEODIST, getName(), encode(firstMember), encode(secondMember), geoUnit);
    }
    
    @Override
    public Map<V, String> hash(V... members) {
        return get(hashAsync(members));
    }
    
    @Override
    public RFuture<Map<V, String>> hashAsync(V... members) {
        List<Object> params = new ArrayList<Object>(members.length + 1);
        params.add(getName());
        for (Object member : members) {
            params.add(encode(member));
        }
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEOHASH", new MapGetAllDecoder((List<Object>)Arrays.asList(members), 0));
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, command, params.toArray());
    }
    
    @Override
    public Map<V, GeoPosition> pos(V... members) {
        return get(posAsync(members));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> posAsync(V... members) {
        List<Object> params = new ArrayList<Object>(members.length + 1);
        params.add(getName());
        for (Object member : members) {
            params.add(encode(member));
        }
        
        MultiDecoder<Map<Object, Object>> decoder = new ListMultiDecoder(0, new GeoPositionDecoder(), 
//                new ObjectListReplayDecoder(ListMultiDecoder.RESET), 
                new GeoPositionMapDecoder((List<Object>)Arrays.asList(members)));
        RedisCommand<Map<Object, Object>> command = new RedisCommand<Map<Object, Object>>("GEOPOS", decoder);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, command, params.toArray());
    }
    
    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit));
    }
    
    @Override
    public RFuture<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUS_RO, getName(), convert(longitude), convert(latitude), radius, geoUnit);
    }

    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit, count));
    }
    
    @Override
    public RFuture<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUS_RO, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "COUNT", count);
    }
    
    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit, geoOrder));
    }
    
    @Override
    public RFuture<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUS_RO, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, geoOrder);
    }

    @Override
    public List<V> radius(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusAsync(longitude, latitude, radius, geoUnit, geoOrder, count));
    }
    
    @Override
    public RFuture<List<V>> radiusAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUS_RO, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "COUNT", count, geoOrder);
    }
    
    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit));
    }
    
    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUS_RO_DISTANCE, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "WITHDIST");
    }
    
    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, count));
    }
    
    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUS_RO_DISTANCE, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "WITHDIST", "COUNT", count);
    }

    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, geoOrder));
    }
    
    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUS_RO_DISTANCE, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "WITHDIST", geoOrder);
    }
    
    @Override
    public Map<V, Double> radiusWithDistance(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, geoOrder, count));
    }

    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUS_RO_DISTANCE, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "WITHDIST", "COUNT", count, geoOrder);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUS_RO_POS, getName(), convert(longitude), convert(latitude), radius, geoUnit, "WITHCOORD");
    }
    
    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit, count));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUS_RO_POS, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "WITHCOORD", "COUNT", count);
    }
    
    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit, geoOrder));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUS_RO_POS, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "WITHCOORD", geoOrder);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusWithPositionAsync(longitude, latitude, radius, geoUnit, geoOrder, count));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUS_RO_POS, getName(), convert(longitude), convert(latitude), 
                radius, geoUnit, "WITHCOORD", "COUNT", count, geoOrder);
    }

    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit) {
        return get(radiusAsync(member, radius, geoUnit));
    }
    
    @Override
    public RFuture<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUSBYMEMBER_RO, getName(), encode(member), radius, geoUnit);
    }
    
    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit, int count) {
        return get(radiusAsync(member, radius, geoUnit, count));
    }
    
    @Override
    public RFuture<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUSBYMEMBER_RO, getName(), encode(member), radius, geoUnit, "COUNT", count);
    }

    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusAsync(member, radius, geoUnit, geoOrder));
    }
    
    @Override
    public RFuture<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUSBYMEMBER_RO, getName(), encode(member), radius, geoUnit, geoOrder);
    }

    @Override
    public List<V> radius(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusAsync(member, radius, geoUnit, geoOrder, count));
    }
    
    @Override
    public RFuture<List<V>> radiusAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GEORADIUSBYMEMBER_RO, getName(), encode(member), radius, geoUnit, "COUNT", count, geoOrder);
    }
    
    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit));
    }
    
    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUSBYMEMBER_RO_DISTANCE, getName(), encode(member), radius, geoUnit, "WITHDIST");
    }

    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit, int count) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit, count));
    }
    
    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUSBYMEMBER_RO_DISTANCE, getName(), encode(member), radius, geoUnit, "WITHDIST", "COUNT", count);
    }

    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit, geoOrder));
    }
    
    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUSBYMEMBER_RO_DISTANCE, getName(), encode(member), radius, geoUnit, "WITHDIST", geoOrder);
    }
    
    @Override
    public Map<V, Double> radiusWithDistance(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusWithDistanceAsync(member, radius, geoUnit, geoOrder, count));
    }
    
    @Override
    public RFuture<Map<V, Double>> radiusWithDistanceAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUSBYMEMBER_RO_DISTANCE, getName(), encode(member), radius, geoUnit, "WITHDIST", "COUNT", count, geoOrder);
    }
    
    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit) {
        return get(radiusWithPositionAsync(member, radius, geoUnit));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUSBYMEMBER_RO_POS, getName(), encode(member), radius, geoUnit, "WITHCOORD");
    }
    
    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit, int count) {
        return get(radiusWithPositionAsync(member, radius, geoUnit, count));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUSBYMEMBER_RO_POS, getName(), encode(member), radius, geoUnit, "WITHCOORD", "COUNT", count);
    }

    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return get(radiusWithPositionAsync(member, radius, geoUnit, geoOrder));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUSBYMEMBER_RO_POS, getName(), encode(member), radius, geoUnit, "WITHCOORD", geoOrder);
    }
    
    @Override
    public Map<V, GeoPosition> radiusWithPosition(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusWithPositionAsync(member, radius, geoUnit, geoOrder, count));
    }
    
    @Override
    public RFuture<Map<V, GeoPosition>> radiusWithPositionAsync(V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.readAsync(getName(), codec, GEORADIUSBYMEMBER_RO_POS, getName(), encode(member), radius, geoUnit, "WITHCOORD", "COUNT", count, geoOrder);
    }

	@Override
	public long radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit) {
		return get(radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit));
	}

	@Override
	public RFuture<Long> radiusStoreToAsync(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit) {
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getName(), convert(longitude), convert(latitude), radius, geoUnit, "STORE", destName);
	}

	@Override
	public long radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
		return get(radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit, count));
	}

	@Override
	public RFuture<Long> radiusStoreToAsync(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getName(), convert(longitude), convert(latitude), radius, geoUnit, "COUNT", count, "STORE", destName);
	}

	@Override
	public long radiusStoreTo(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
		return get(radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit, geoOrder, count));
	}

	@Override
	public RFuture<Long> radiusStoreToAsync(String destName, double longitude, double latitude, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.GEORADIUS_STORE, getName(), convert(longitude), convert(latitude), radius, geoUnit, geoOrder, "COUNT", count, "STORE", destName);
	}

	@Override
	public long radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit) {
		return get(radiusStoreToAsync(destName, member, radius, geoUnit));
	}

	@Override
	public RFuture<Long> radiusStoreToAsync(String destName, V member, double radius, GeoUnit geoUnit) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getName(), encode(member), radius, geoUnit, "STORE", destName);
	}

	@Override
	public long radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit, int count) {
		return get(radiusStoreToAsync(destName, member, radius, geoUnit, count));
	}

	@Override
	public RFuture<Long> radiusStoreToAsync(String destName, V member, double radius, GeoUnit geoUnit, int count) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getName(), encode(member), radius, geoUnit, "COUNT", count, "STORE", destName);
	}

	@Override
	public long radiusStoreTo(String destName, V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return get(radiusStoreToAsync(destName, member, radius, geoUnit, geoOrder, count));
	}

	@Override
	public RFuture<Long> radiusStoreToAsync(String destName, V member, double radius, GeoUnit geoUnit, GeoOrder geoOrder, int count) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.GEORADIUSBYMEMBER_STORE, getName(), encode(member), radius, geoUnit, geoOrder, "COUNT", count, "STORE", destName);
	}

}
