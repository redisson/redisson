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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Google's Snappy compression codec.
 * Uses inner <code>Codec</code> to convert object to binary stream.
 * <code>Kryo5Codec</code> used by default.
 * <p>
 * Based on <a href="https://github.com/xerial/snappy-java">https://github.com/xerial/snappy-java</a>
 *
 * Fully thread-safe.
 *
 * @see org.redisson.codec.Kryo5Codec
 *
 * @author Nikita Koksharov
 *
 */
public class SnappyCodecV2 extends BaseCodec {

    private final Codec innerCodec;

    public SnappyCodecV2() {
        this(new Kryo5Codec());
    }

    public SnappyCodecV2(Codec innerCodec) {
        this.innerCodec = innerCodec;
    }

    public SnappyCodecV2(ClassLoader classLoader) {
        this(new Kryo5Codec(classLoader));
    }
    
    public SnappyCodecV2(ClassLoader classLoader, SnappyCodecV2 codec) throws ReflectiveOperationException {
        this(copy(classLoader, codec.innerCodec));
    }
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            if (buf.isDirect() && buf.nioBufferCount() == 1) {
                ByteBuffer source = buf.nioBuffer(buf.readerIndex(), buf.readableBytes());

                int size = Snappy.uncompressedLength(source);
                checkDecompressionSize(size);

                ByteBuf out = ByteBufAllocator.DEFAULT.directBuffer(size);
                try {
                    ByteBuffer target = out.nioBuffer(out.writerIndex(), size);
                    out.writerIndex(out.writerIndex() + Snappy.uncompress(source, target));
                    return innerCodec.getValueDecoder().decode(out, state);
                } finally {
                    out.release();
                }
            }

            // see the encoder: the copy is the price of a non-direct buffer reaching a native call
            byte[] compressed = ByteBufUtil.getBytes(buf);
            checkDecompressionSize(Snappy.uncompressedLength(compressed));
            ByteBuf out = Unpooled.wrappedBuffer(Snappy.uncompress(compressed));
            try {
                return innerCodec.getValueDecoder().decode(out, state);
            } finally {
                out.release();
            }
        }
    };

    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf encoded = innerCodec.getValueEncoder().encode(in);
            try {
                int size = encoded.readableBytes();

                ByteBuf out = ByteBufAllocator.DEFAULT.directBuffer(Snappy.maxCompressedLength(size));
                boolean complete = false;
                try {
                    if (encoded.isDirect() && encoded.nioBufferCount() == 1) {
                        ByteBuffer source = encoded.nioBuffer(encoded.readerIndex(), size);
                        ByteBuffer target = out.nioBuffer(out.writerIndex(), out.writableBytes());
                        out.writerIndex(out.writerIndex() + Snappy.compress(source, target));
                    } else {
                        out.writeBytes(Snappy.compress(ByteBufUtil.getBytes(encoded)));
                    }
                    complete = true;
                    return out;
                } finally {
                    if (!complete) {
                        out.release();
                    }
                }
            } finally {
                encoded.release();
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
    
}
