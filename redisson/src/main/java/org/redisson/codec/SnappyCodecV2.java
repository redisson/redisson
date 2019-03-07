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
package org.redisson.codec;

import java.io.IOException;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.xerial.snappy.Snappy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Google's Snappy compression codec.
 * Uses inner <code>Codec</code> to convert object to binary stream.
 * <code>FstCodec</code> used by default.
 * <p>
 * Based on <a href="https://github.com/xerial/snappy-java">https://github.com/xerial/snappy-java</a>
 *
 * @see org.redisson.codec.FstCodec
 *
 * @author Nikita Koksharov
 *
 */
public class SnappyCodecV2 extends BaseCodec {

    private final Codec innerCodec;

    public SnappyCodecV2() {
        this(new FstCodec());
    }

    public SnappyCodecV2(Codec innerCodec) {
        this.innerCodec = innerCodec;
    }

    public SnappyCodecV2(ClassLoader classLoader) {
        this(new FstCodec(classLoader));
    }
    
    public SnappyCodecV2(ClassLoader classLoader, SnappyCodecV2 codec) throws ReflectiveOperationException {
        this(copy(classLoader, codec.innerCodec));
    }
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            bytes = Snappy.uncompress(bytes);
            ByteBuf bf = Unpooled.wrappedBuffer(bytes);
            try {
                return innerCodec.getValueDecoder().decode(bf, state);
            } finally {
                bf.release();
            }
        }
    };

    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf encoded = innerCodec.getValueEncoder().encode(in);
            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.readBytes(bytes);
            encoded.release();
            byte[] res = Snappy.compress(bytes);
            return Unpooled.wrappedBuffer(res);
        }
    };

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }
    
}
