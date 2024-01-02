/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.codec.BaseEventCodec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class JCacheEventCodec extends BaseEventCodec {

    private final boolean sync;
    private final boolean expectOldValueInMsg;
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            List<Object> result = new ArrayList<>();

            Object key = JCacheEventCodec.this.decode(buf, state, codec.getMapKeyDecoder());
            result.add(key);

            Object value = JCacheEventCodec.this.decode(buf, state, codec.getMapValueDecoder());
            result.add(value);

            if (expectOldValueInMsg) {
                if (buf.getShortLE(buf.readerIndex()) != -1) {
                    Object oldValue = JCacheEventCodec.this.decode(buf, state, codec.getMapValueDecoder());
                    result.add(oldValue);
                } else {
                    buf.readShortLE();
                    result.add(null);
                }
            }
            
            if (sync) {
                double syncId = buf.readDoubleLE();
                result.add(syncId);
            }
            
            return result;
        }
    };

    public JCacheEventCodec(Codec codec, OSType osType, boolean sync) {
        super(codec, osType);
        this.sync = sync;
        this.expectOldValueInMsg = false;
    }

    public JCacheEventCodec(Codec codec, OSType osType, boolean sync, boolean expectOldValueInMsg) {
        super(codec, osType);
        this.sync = sync;
        this.expectOldValueInMsg = expectOldValueInMsg;
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

}
