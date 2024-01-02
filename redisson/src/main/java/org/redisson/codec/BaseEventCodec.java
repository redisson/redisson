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
package org.redisson.codec;

import io.netty.buffer.ByteBuf;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;

/**
 *
 * @author Nikita Koksharov
 *
 */
public abstract class BaseEventCodec implements Codec {

    public enum OSType {WINDOWS, HPNONSTOP}

    protected final Codec codec;
    protected final OSType osType;

    public BaseEventCodec(Codec codec, OSType osType) {
        this.codec = codec;
        this.osType = osType;
    }

    protected Object decode(ByteBuf buf, State state, Decoder<?> decoder) throws IOException {
        int keyLen;
        if (osType == OSType.WINDOWS) {
            keyLen = buf.readIntLE();
        } else if (osType == OSType.HPNONSTOP) {
            keyLen = (int) buf.readLong();
        } else {
            keyLen = (int) buf.readLongLE();
        }
        ByteBuf keyBuf = buf.readSlice(keyLen);
        Object key = decoder.decode(keyBuf, state);
        return key;
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
    public Encoder getValueEncoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }


}
