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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.decoder.ListMultiDecoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metric;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ByteBufferGeoResultsDecoder implements MultiDecoder<GeoResults<GeoLocation<ByteBuffer>>> {

    private final Metric metric;
    
    public ByteBufferGeoResultsDecoder() {
        this(null);
    }
    
    public ByteBufferGeoResultsDecoder(Metric metric) {
        super();
        this.metric = metric;
    }

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return ListMultiDecoder.RESET;
    }
    
    @Override
    public GeoResults<GeoLocation<ByteBuffer>> decode(List<Object> parts, State state) {
        List<GeoResult<GeoLocation<ByteBuffer>>> result = new ArrayList<GeoResult<GeoLocation<ByteBuffer>>>();
        for (Object object : parts) {
            if (object instanceof List) {
                List<Object> vals = ((List<Object>) object);
                
                if (metric != null) {
                    GeoLocation<ByteBuffer> location = new GeoLocation<ByteBuffer>(ByteBuffer.wrap((byte[])vals.get(0)), null);
                    result.add(new GeoResult<GeoLocation<ByteBuffer>>(location, new Distance((Double)vals.get(1), metric)));
                } else {
                    GeoLocation<ByteBuffer> location = new GeoLocation<ByteBuffer>(ByteBuffer.wrap((byte[])vals.get(0)), (Point)vals.get(1));
                    result.add(new GeoResult<GeoLocation<ByteBuffer>>(location, null));
                }
            } else {
                GeoLocation<ByteBuffer> location = new GeoLocation<ByteBuffer>(ByteBuffer.wrap((byte[])object), null);
                result.add(new GeoResult<GeoLocation<ByteBuffer>>(location, new Distance(0)));
            }
        }
        return new GeoResults<GeoLocation<ByteBuffer>>(result);
    }

}
