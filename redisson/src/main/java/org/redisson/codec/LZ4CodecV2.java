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
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorOutputStream;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;

/**
 * LZ4 compression codec.
 * Uses inner <code>Codec</code> to convert object to binary stream.
 * <code>Kryo5Codec</code> used by default.
 *
 * Fully thread-safe.
 *
 * https://github.com/apache/commons-compress
 *
 * @see Kryo5Codec
 *
 * @author Nikita Koksharov
 *
 */
public class LZ4CodecV2 extends BaseCodec {

    private final Codec innerCodec;

    public LZ4CodecV2() {
        this(new Kryo5Codec());
    }

    public LZ4CodecV2(Codec innerCodec) {
        this.innerCodec = innerCodec;
    }

    public LZ4CodecV2(ClassLoader classLoader) {
        this(new Kryo5Codec(classLoader));
    }

    public LZ4CodecV2(ClassLoader classLoader, LZ4CodecV2 codec) throws ReflectiveOperationException {
        this(copy(classLoader, codec.innerCodec));
    }
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            int decompressionSize = buf.readInt();
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer(decompressionSize);
            try {
                ByteBufInputStream ios = new ByteBufInputStream(buf);
                BlockLZ4CompressorInputStream in = new BlockLZ4CompressorInputStream(ios);
                out.writeBytes(in, buf.readableBytes());
                in.close();
                return innerCodec.getValueDecoder().decode(out, state);
            } finally {
                out.release();
            }
        }
    };

    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf bytes = null;
            try {
                ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
                bytes = innerCodec.getValueEncoder().encode(in);
                out.writeInt(bytes.readableBytes());
                ByteBufOutputStream baos = new ByteBufOutputStream(out);
                BlockLZ4CompressorOutputStream compressor = new BlockLZ4CompressorOutputStream(baos);
                bytes.getBytes(bytes.readerIndex(), compressor, bytes.readableBytes());
                compressor.close();
                return out;
            } finally {
                if (bytes != null) {
                    bytes.release();
                }
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
