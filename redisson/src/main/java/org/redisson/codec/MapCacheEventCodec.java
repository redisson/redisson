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
package org.redisson.codec;

import io.netty.buffer.ByteBuf;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MapCacheEventCodec extends BaseEventCodec {

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            List<Object> result = new ArrayList<Object>(3);

            Object key = MapCacheEventCodec.this.decode(buf, state, codec.getMapKeyDecoder());
            result.add(key);

            Object value = MapCacheEventCodec.this.decode(buf, state, codec.getMapValueDecoder());
            result.add(value);
            
            if (buf.isReadable()) {
                Object oldValue = MapCacheEventCodec.this.decode(buf, state, codec.getMapValueDecoder());
                result.add(oldValue);
            }
            
            return result;
        }
    };

    public MapCacheEventCodec(Codec codec, OSType osType) {
        super(codec, osType);
    }
    
    public MapCacheEventCodec(ClassLoader classLoader, MapCacheEventCodec codec) {
        super(newCodec(classLoader, codec), codec.osType);
    }

    private static Codec newCodec(ClassLoader classLoader, MapCacheEventCodec codec) {
        try {
            return codec.codec.getClass().getConstructor(ClassLoader.class, codec.codec.getClass()).newInstance(classLoader, codec.codec);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

}
