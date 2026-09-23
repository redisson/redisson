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

import com.github.luben.zstd.*;
import io.netty.buffer.*;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ZStandard codec.
 * Uses inner <code>Codec</code> to convert object to binary stream.
 * <code>Kryo5Codec</code> is used by default.
 * <p>
 * Based on <a href="https://github.com/luben/zstd-jni">https://github.com/luben/zstd-jni</a>
 *
 * Fully thread-safe.
 *
 * @see Kryo5Codec
 *
 * @author Nikita Koksharov
 *
 */
public class ZStdCodec extends BaseCodec {

    public static final int DEFAULT_MAX_POOLED = 128;

    private final Queue<ZstdCompressCtx> compressors = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pooledCompressors = new AtomicInteger();
    private final Queue<ZstdDecompressCtx> decompressors = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pooledDecompressors = new AtomicInteger();

    private final int maxPooled;
    private final Codec innerCodec;

    public ZStdCodec() {
        this(new Kryo5Codec());
    }

    public ZStdCodec(Codec innerCodec) {
        this(innerCodec, DEFAULT_MAX_POOLED);
    }

    public ZStdCodec(ClassLoader classLoader) {
        this(new Kryo5Codec(classLoader));
    }

    public ZStdCodec(ClassLoader classLoader, ZStdCodec codec) throws ReflectiveOperationException {
        this(copy(classLoader, codec.innerCodec));
    }

    public ZStdCodec(Codec innerCodec, int maxPooled) {
        this.innerCodec = Objects.requireNonNull(innerCodec, "innerCodec");
        if (maxPooled < 0) {
            throw new IllegalArgumentException("maxPooled must not be negative: " + maxPooled);
        }
        this.maxPooled = maxPooled;
    }

    private final Encoder encoder = new Encoder() {
        @Override
        @SuppressWarnings("NestedTryDepth")
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf encoded = innerCodec.getValueEncoder().encode(in);
            try {
                int size = encoded.readableBytes();
                int bound = (int) Zstd.compressBound(size);

                ByteBuf out = ByteBufAllocator.DEFAULT.directBuffer(Integer.BYTES + bound);
                boolean complete = false;
                try {
                    out.writeInt(size);

                    ZstdCompressCtx context = compressors.poll();
                    if (context == null) {
                        context = new ZstdCompressCtx();
                    } else {
                        pooledCompressors.decrementAndGet();
                    }
                    try {
                        if (encoded.isDirect() && encoded.nioBufferCount() == 1) {
                            ByteBuffer source = encoded.nioBuffer(encoded.readerIndex(), size);
                            ByteBuffer target = out.nioBuffer(out.writerIndex(), bound);
                            out.writerIndex(out.writerIndex() + context.compress(target, source));
                        } else {
                            out.writeBytes(context.compress(ByteBufUtil.getBytes(encoded)));
                        }
                    } finally {
                        if (pooledCompressors.incrementAndGet() <= maxPooled) {
                            compressors.offer(context);
                        } else {
                            pooledCompressors.decrementAndGet();
                            context.close();
                        }
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

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            int size = buf.readInt();
            checkDecompressionSize(size);

            ZstdDecompressCtx context = decompressors.poll();
            if (context == null) {
                context = new ZstdDecompressCtx();
            } else {
                pooledDecompressors.decrementAndGet();
            }
            try {
                if (buf.isDirect() && buf.nioBufferCount() == 1) {
                    ByteBuf out = ByteBufAllocator.DEFAULT.directBuffer(size);
                    try {
                        ByteBuffer source = buf.nioBuffer(buf.readerIndex(), buf.readableBytes());
                        ByteBuffer target = out.nioBuffer(out.writerIndex(), size);
                        out.writerIndex(out.writerIndex() + context.decompress(target, source));
                        return innerCodec.getValueDecoder().decode(out, state);
                    } finally {
                        out.release();
                    }
                }

                byte[] plain = context.decompress(ByteBufUtil.getBytes(buf), size);
                ByteBuf out = Unpooled.wrappedBuffer(plain);
                try {
                    return innerCodec.getValueDecoder().decode(out, state);
                } finally {
                    out.release();
                }
            } finally {
                if (pooledDecompressors.incrementAndGet() <= maxPooled) {
                    decompressors.offer(context);
                } else {
                    pooledDecompressors.decrementAndGet();
                    context.close();
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
