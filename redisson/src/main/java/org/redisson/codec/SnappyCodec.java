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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.compression.Snappy;
import io.netty.util.concurrent.FastThreadLocal;

/**
 * Snappy compression codec.
 * Uses inner <code>Codec</code> to convert object to binary stream.
 * <code>FstCodec</code> used by default.
 *
 * @see org.redisson.codec.FstCodec
 *
 * @author Nikita Koksharov
 *
 */
public class SnappyCodec extends BaseCodec {

    private static final FastThreadLocal<Snappy> SNAPPY_DECODER = new FastThreadLocal<Snappy>() {
        protected Snappy initialValue() {
            return new Snappy();
        };
    };
    
    private static final FastThreadLocal<Snappy> SNAPPY_ENCODER = new FastThreadLocal<Snappy>() {
        protected Snappy initialValue() {
            return new Snappy();
        };
    };

    private final Codec innerCodec;

    public SnappyCodec() {
        this(new FstCodec());
    }

    public SnappyCodec(Codec innerCodec) {
        this.innerCodec = innerCodec;
    }

    public SnappyCodec(ClassLoader classLoader) {
        this(new FstCodec(classLoader));
    }
    
    public SnappyCodec(ClassLoader classLoader, SnappyCodec codec) throws ReflectiveOperationException {
        this(copy(classLoader, codec.innerCodec));
    }
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                while (buf.isReadable()) {
                    int chunkSize = buf.readInt();
                    ByteBuf chunk = buf.readSlice(chunkSize);
                    SNAPPY_DECODER.get().decode(chunk, out);
                    SNAPPY_DECODER.get().reset();
                }
                return innerCodec.getValueDecoder().decode(out, state);
            } finally {
                SNAPPY_DECODER.get().reset();
                out.release();
            }
        }
    };

    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf buf = innerCodec.getValueEncoder().encode(in);
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                int chunksAmount = (int) Math.ceil(buf.readableBytes() / (double) Short.MAX_VALUE);
                for (int i = 1; i <= chunksAmount; i++) {
                    int chunkSize = Math.min(Short.MAX_VALUE, buf.readableBytes());

                    ByteBuf chunk = buf.readSlice(chunkSize);
                    int lenIndex = out.writerIndex();
                    out.writeInt(0);
                    SNAPPY_ENCODER.get().encode(chunk, out, chunk.readableBytes());
                    int compressedDataLength = out.writerIndex() - 4 - lenIndex;
                    out.setInt(lenIndex, compressedDataLength);
                }
                return out;
            } finally {
                buf.release();
                SNAPPY_ENCODER.get().reset();
            }
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
    
    @Override
    public ClassLoader getClassLoader() {
        return innerCodec.getClassLoader();
    }
    
}
