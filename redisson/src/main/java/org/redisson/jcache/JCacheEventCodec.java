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
package org.redisson.jcache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class JCacheEventCodec implements Codec {

    private final Codec codec;
    private final boolean sync;
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            List<Object> result = new ArrayList<Object>();
            int keyLen;
            if (PlatformDependent.isWindows()) {
                keyLen = buf.readIntLE();
            } else {
                keyLen = (int) buf.readLongLE();
            }
            ByteBuf keyBuf = buf.readSlice(keyLen);
            Object key = codec.getMapKeyDecoder().decode(keyBuf, state);
            result.add(key);

            int valueLen;
            if (PlatformDependent.isWindows()) {
                valueLen = buf.readIntLE();
            } else {
                valueLen = (int) buf.readLongLE();
            }
            ByteBuf valueBuf = buf.readSlice(valueLen);
            Object value = codec.getMapValueDecoder().decode(valueBuf, state);
            result.add(value);
            
            if (sync) {
                double syncId = buf.readDoubleLE();
                result.add(syncId);
            }
            
            return result;
        }
    };

    public JCacheEventCodec(Codec codec, boolean sync) {
        super();
        this.codec = codec;
        this.sync = sync;
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Encoder getMapValueEncoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Encoder getMapKeyEncoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

}
