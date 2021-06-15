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

import org.redisson.client.handler.State;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class GeoResultsDecoder implements MultiDecoder<GeoResults<GeoLocation<byte[]>>> {

    private final Metric metric;
    
    public GeoResultsDecoder() {
        this(null);
    }
    
    public GeoResultsDecoder(Metric metric) {
        super();
        this.metric = metric;
    }

    @Override
    public GeoResults<GeoLocation<byte[]>> decode(List<Object> parts, State state) {
        List<GeoResult<GeoLocation<byte[]>>> result = new ArrayList<GeoResult<GeoLocation<byte[]>>>();
        for (Object object : parts) {
            if (object instanceof List) {
                List<Object> vals = ((List<Object>) object);
                
                if (metric != null) {
                    GeoLocation<byte[]> location = new GeoLocation<byte[]>((byte[])vals.get(0), null);
                    result.add(new GeoResult<GeoLocation<byte[]>>(location, new Distance((Double)vals.get(1), metric)));
                } else {
                    GeoLocation<byte[]> location = new GeoLocation<byte[]>((byte[])vals.get(0), (Point)vals.get(1));
                    result.add(new GeoResult<GeoLocation<byte[]>>(location, new Distance(0)));
                }
            } else {
                GeoLocation<byte[]> location = new GeoLocation<byte[]>((byte[])object, null);
                result.add(new GeoResult<GeoLocation<byte[]>>(location, new Distance(0)));
            }
        }
        return new GeoResults<GeoLocation<byte[]>>(result);
    }

}
