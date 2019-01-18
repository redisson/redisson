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
import java.nio.ByteBuffer;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;

/**
 * LZ4 compression codec.
 * Uses inner <code>Codec</code> to convert object to binary stream.
 * <code>FstCodec</code> used by default.
 *
 * https://github.com/jpountz/lz4-java
 *
 * @see org.redisson.codec.FstCodec
 *
 * @author Nikita Koksharov
 *
 */
public class LZ4Codec extends BaseCodec {

    private static final int DECOMPRESSION_HEADER_SIZE = Integer.SIZE / 8;
    private final LZ4Factory factory = LZ4Factory.fastestInstance();

    private final Codec innerCodec;

    public LZ4Codec() {
        this(new FstCodec());
    }

    public LZ4Codec(Codec innerCodec) {
        this.innerCodec = innerCodec;
    }
    
    public LZ4Codec(ClassLoader classLoader) {
        this(new FstCodec(classLoader));
    }

    public LZ4Codec(ClassLoader classLoader, LZ4Codec codec) {
        this(copy(classLoader, codec.innerCodec));
    }
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            int decompressSize = buf.readInt();
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer(decompressSize);
            try {
                LZ4SafeDecompressor decompressor = factory.safeDecompressor();
                ByteBuffer outBuffer = out.internalNioBuffer(out.writerIndex(), out.writableBytes());
                int pos = outBuffer.position();
                decompressor.decompress(buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()), outBuffer);
                int compressedLength = outBuffer.position() - pos;
                out.writerIndex(compressedLength);
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
                LZ4Compressor compressor = factory.fastCompressor();
                bytes = innerCodec.getValueEncoder().encode(in);
                ByteBuffer srcBuf = bytes.internalNioBuffer(bytes.readerIndex(), bytes.readableBytes());
                
                int outMaxLength = compressor.maxCompressedLength(bytes.readableBytes());
                ByteBuf out = ByteBufAllocator.DEFAULT.buffer(outMaxLength + DECOMPRESSION_HEADER_SIZE);
                out.writeInt(bytes.readableBytes());
                ByteBuffer outBuf = out.internalNioBuffer(out.writerIndex(), out.writableBytes());
                int pos = outBuf.position();
                
                compressor.compress(srcBuf, outBuf);
                
                int compressedLength = outBuf.position() - pos;
                out.writerIndex(out.writerIndex() + compressedLength);
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
