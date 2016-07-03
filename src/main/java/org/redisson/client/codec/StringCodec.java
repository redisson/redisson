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
import java.nio.charset.Charset;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class StringCodec implements Codec {

    public static final StringCodec INSTANCE = new StringCodec();

    private final Charset charset;

    private final Encoder encoder = new Encoder() {
        @Override
        public byte[] encode(Object in) throws IOException {
            return in.toString().getBytes(charset);
        }
    };

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) {
            String str = buf.toString(charset);
            buf.readerIndex(buf.readableBytes());
            return str;
        }
    };

    public StringCodec() {
        this(CharsetUtil.UTF_8);
    }

    public StringCodec(String charsetName) {
	this(Charset.forName(charsetName));
    }

    public StringCodec(Charset charset) {
        this.charset = charset;
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return getValueDecoder();
    }

    @Override
    public Encoder getMapValueEncoder() {
        return getValueEncoder();
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return getValueDecoder();
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return getValueEncoder();
    }

}
