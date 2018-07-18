/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.reactive;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonGeo;
import org.redisson.api.GeoEntry;
import org.redisson.api.GeoOrder;
import org.redisson.api.GeoPosition;
import org.redisson.api.GeoUnit;
import org.redisson.api.RFuture;
import org.redisson.api.RGeoAsync;
import org.redisson.api.RGeoReactive;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonGeoReactive<V> extends RedissonScoredSortedSetReactive<V> implements RGeoReactive<V> {

    private final RGeoAsync<V> instance;
    
    public RedissonGeoReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor, name, new RedissonGeo<V>(commandExecutor, name, null));
    }

    public RedissonGeoReactive(CommandReactiveExecutor commandExecutor, String name, RGeoAsync<V> instance) {
        super(commandExecutor, name, instance);
        this.instance = instance;
    }
    
    public RedissonGeoReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this(codec, commandExecutor, name, new RedissonGeo<V>(codec, commandExecutor, name, null));
    }

    public RedissonGeoReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, RGeoAsync<V> instance) {
        super(codec, commandExecutor, name, instance);
        this.instance = instance;
    }

    @Override
    public Publisher<Long> add(final double longitude, final double latitude, final V member) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.addAsync(longitude, latitude, member);
            }
        });
    }

    @Override
    public Publisher<Long> add(final GeoEntry... entries) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.addAsync(entries);
            }
        });
    }

    @Override
    public Publisher<Double> dist(final V firstMember, final V secondMember, final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<Double>>() {
            @Override
            public RFuture<Double> get() {
                return instance.distAsync(firstMember, secondMember, geoUnit);
            }
        });
    }

    @Override
    public Publisher<Map<V, String>> hash(final V... members) {
        return reactive(new Supplier<RFuture<Map<V, String>>>() {
            @Override
            public RFuture<Map<V, String>> get() {
                return instance.hashAsync(members);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> pos(final V... members) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.posAsync(members);
            }
        });
    }

    @Override
    public Publisher<List<V>> radius(final double longitude, final double latitude, final double radius, final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.radiusAsync(longitude, latitude, radius, geoUnit);
            }
        });
    }

    @Override
    public Publisher<List<V>> radius(final double longitude, final double latitude, final double radius, final GeoUnit geoUnit, final int count) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.radiusAsync(longitude, latitude, radius, geoUnit, count);
            }
        });
    }

    @Override
    public Publisher<List<V>> radius(final double longitude, final double latitude, final double radius, final GeoUnit geoUnit,
            final GeoOrder geoOrder) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.radiusAsync(longitude, latitude, radius, geoUnit, geoOrder);
            }
        });
    }

    @Override
    public Publisher<List<V>> radius(final double longitude, final double latitude, final double radius, final GeoUnit geoUnit,
            final GeoOrder geoOrder, final int count) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.radiusAsync(longitude, latitude, radius, geoUnit, geoOrder, count);
            }
        });
    }

    @Override
    public Publisher<Map<V, Double>> radiusWithDistance(final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<Map<V, Double>>>() {
            @Override
            public RFuture<Map<V, Double>> get() {
                return instance.radiusWithDistanceAsync(longitude, latitude, radius, geoUnit);
            }
        });
    }

    @Override
    public Publisher<Map<V, Double>> radiusWithDistance(final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit, final int count) {
        return reactive(new Supplier<RFuture<Map<V, Double>>>() {
            @Override
            public RFuture<Map<V, Double>> get() {
                return instance.radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, count);
            }
        });
    }

    @Override
    public Publisher<Map<V, Double>> radiusWithDistance(final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit, final GeoOrder geoOrder) {
        return reactive(new Supplier<RFuture<Map<V, Double>>>() {
            @Override
            public RFuture<Map<V, Double>> get() {
                return instance.radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, geoOrder);
            }
        });
    }

    @Override
    public Publisher<Map<V, Double>> radiusWithDistance(final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit, final GeoOrder geoOrder, final int count) {
        return reactive(new Supplier<RFuture<Map<V, Double>>>() {
            @Override
            public RFuture<Map<V, Double>> get() {
                return instance.radiusWithDistanceAsync(longitude, latitude, radius, geoUnit, geoOrder, count);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> radiusWithPosition(final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.radiusWithPositionAsync(longitude, latitude, radius, geoUnit);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> radiusWithPosition(final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit, final int count) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.radiusWithPositionAsync(longitude, latitude, radius, geoUnit, count);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> radiusWithPosition(final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit, final GeoOrder geoOrder) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.radiusWithPositionAsync(longitude, latitude, radius, geoUnit, geoOrder);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> radiusWithPosition(final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit, final GeoOrder geoOrder, final int count) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.radiusWithPositionAsync(longitude, latitude, radius, geoUnit, geoOrder, count);
            }
        });
    }

    @Override
    public Publisher<List<V>> radius(final V member, final double radius, final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.radiusAsync(member, radius, geoUnit);
            }
        });
    }

    @Override
    public Publisher<List<V>> radius(final V member, final double radius, final GeoUnit geoUnit, final int count) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.radiusAsync(member, radius, geoUnit, count);
            }
        });
    }

    @Override
    public Publisher<List<V>> radius(final V member, final double radius, final GeoUnit geoUnit, final GeoOrder geoOrder) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.radiusAsync(member, radius, geoUnit, geoOrder);
            }
        });
    }

    @Override
    public Publisher<List<V>> radius(final V member, final double radius, final GeoUnit geoUnit, final GeoOrder geoOrder, final int count) {
        return reactive(new Supplier<RFuture<List<V>>>() {
            @Override
            public RFuture<List<V>> get() {
                return instance.radiusAsync(member, radius, geoUnit, geoOrder, count);
            }
        });
    }

    @Override
    public Publisher<Map<V, Double>> radiusWithDistance(final V member, final double radius, final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<Map<V, Double>>>() {
            @Override
            public RFuture<Map<V, Double>> get() {
                return instance.radiusWithDistanceAsync(member, radius, geoUnit);
            }
        });
    }

    @Override
    public Publisher<Map<V, Double>> radiusWithDistance(final V member, final double radius, final GeoUnit geoUnit, final int count) {
        return reactive(new Supplier<RFuture<Map<V, Double>>>() {
            @Override
            public RFuture<Map<V, Double>> get() {
                return instance.radiusWithDistanceAsync(member, radius, geoUnit, count);
            }
        });
    }

    @Override
    public Publisher<Map<V, Double>> radiusWithDistance(final V member, final double radius, final GeoUnit geoUnit, final GeoOrder geoOrder) {
        return reactive(new Supplier<RFuture<Map<V, Double>>>() {
            @Override
            public RFuture<Map<V, Double>> get() {
                return instance.radiusWithDistanceAsync(member, radius, geoUnit, geoOrder);
            }
        });
    }

    @Override
    public Publisher<Map<V, Double>> radiusWithDistance(final V member, final double radius, final GeoUnit geoUnit, final GeoOrder geoOrder,
            final int count) {
        return reactive(new Supplier<RFuture<Map<V, Double>>>() {
            @Override
            public RFuture<Map<V, Double>> get() {
                return instance.radiusWithDistanceAsync(member, radius, geoUnit, geoOrder, count);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> radiusWithPosition(final V member, final double radius, final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.radiusWithPositionAsync(member, radius, geoUnit);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> radiusWithPosition(final V member, final double radius, final GeoUnit geoUnit, final int count) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.radiusWithPositionAsync(member, radius, geoUnit, count);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> radiusWithPosition(final V member, final double radius, final GeoUnit geoUnit,
            final GeoOrder geoOrder) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.radiusWithPositionAsync(member, radius, geoUnit, geoOrder);
            }
        });
    }

    @Override
    public Publisher<Map<V, GeoPosition>> radiusWithPosition(final V member, final double radius, final GeoUnit geoUnit,
            final GeoOrder geoOrder, final int count) {
        return reactive(new Supplier<RFuture<Map<V, GeoPosition>>>() {
            @Override
            public RFuture<Map<V, GeoPosition>> get() {
                return instance.radiusWithPositionAsync(member, radius, geoUnit, geoOrder, count);
            }
        });
    }

    @Override
    public Publisher<Long> radiusStoreTo(final String destName, final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit);
            }
        });
    }

    @Override
    public Publisher<Long> radiusStoreTo(final String destName, final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit, final int count) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit, count);
            }
        });
    }

    @Override
    public Publisher<Long> radiusStoreTo(final String destName, final double longitude, final double latitude, final double radius,
            final GeoUnit geoUnit, final GeoOrder geoOrder, final int count) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.radiusStoreToAsync(destName, longitude, latitude, radius, geoUnit, geoOrder, count);
            }
        });
    }

    @Override
    public Publisher<Long> radiusStoreTo(final String destName, final V member, final double radius, final GeoUnit geoUnit) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.radiusStoreToAsync(destName, member, radius, geoUnit);
            }
        });
    }

    @Override
    public Publisher<Long> radiusStoreTo(final String destName, final V member, final double radius, final GeoUnit geoUnit, final int count) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.radiusStoreToAsync(destName, member, radius, geoUnit, count);
            }
        });
    }

    @Override
    public Publisher<Long> radiusStoreTo(final String destName, final V member, final double radius, final GeoUnit geoUnit, final GeoOrder geoOrder,
            final int count) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.radiusStoreToAsync(destName, member, radius, geoUnit, geoOrder, count);
            }
        });
    }


}
