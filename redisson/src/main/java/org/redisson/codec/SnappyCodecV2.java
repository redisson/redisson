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
package org.redisson.codec;

import java.io.IOException;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

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
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            SnappyInputStream input = new SnappyInputStream(new ByteBufInputStream(buf));
            ByteBuf bf = ByteBufAllocator.DEFAULT.buffer(buf.readableBytes());
            bf.writeBytes(input, buf.readableBytes());
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
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer(encoded.readableBytes());
            try {
                SnappyOutputStream output = new SnappyOutputStream(new ByteBufOutputStream(out));
                encoded.readBytes(output, encoded.readableBytes());
                output.flush();
            } finally {
                encoded.release();
            }
            return out;
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
