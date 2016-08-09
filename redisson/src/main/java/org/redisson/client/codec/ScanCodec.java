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
package org.redisson.client.codec;

import java.io.IOException;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.client.protocol.decoder.ScanObjectEntry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ScanCodec implements Codec {

    private final Codec delegate;
    private final Codec mapValueCodec;

    public ScanCodec(Codec delegate) {
        this(delegate, null);
    }

    public ScanCodec(Codec delegate, Codec mapValueCodec) {
        this.delegate = delegate;
        this.mapValueCodec = mapValueCodec;
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return delegate.getValueDecoder();
    }

    @Override
    public Encoder getValueEncoder() {
        return delegate.getValueEncoder();
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                ByteBuf b = Unpooled.copiedBuffer(buf);
                Codec c = delegate;
                if (mapValueCodec != null) {
                    c = mapValueCodec;
                }
                Object val = c.getMapValueDecoder().decode(buf, state);
                return new ScanObjectEntry(b, val);
            }
        };
    }

    @Override
    public Encoder getMapValueEncoder() {
        Codec c = delegate;
        if (mapValueCodec != null) {
            c = mapValueCodec;
        }

        return c.getMapValueEncoder();
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return new Decoder<Object>() {
            @Override
            public Object decode(ByteBuf buf, State state) throws IOException {
                ByteBuf b = Unpooled.copiedBuffer(buf);
                Object val = delegate.getMapKeyDecoder().decode(buf, state);
                return new ScanObjectEntry(b, val);
            }
        };
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return delegate.getMapKeyEncoder();
    }

}
