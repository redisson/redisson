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

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import io.netty.buffer.*;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;

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

    private final Codec innerCodec;

    public ZStdCodec() {
        this(new Kryo5Codec());
    }

    public ZStdCodec(Codec innerCodec) {
        this.innerCodec = innerCodec;
    }

    public ZStdCodec(ClassLoader classLoader) {
        this(new Kryo5Codec(classLoader));
    }

    public ZStdCodec(ClassLoader classLoader, ZStdCodec codec) throws ReflectiveOperationException {
        this(copy(classLoader, codec.innerCodec));
    }
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            int size = buf.readInt();
            ZstdInputStream s = new ZstdInputStream(new ByteBufInputStream(buf));
            byte[] bytes = new byte[size];
            s.read(bytes);
            s.close();

            ByteBuf in = Unpooled.wrappedBuffer(bytes);
            try {
                return innerCodec.getValueDecoder().decode(in, state);
            } finally {
                in.release();
            }
        }
    };

    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf encoded = innerCodec.getValueEncoder().encode(in);

            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            ZstdOutputStream o = new ZstdOutputStream(new ByteBufOutputStream(out));

            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.readBytes(bytes);
            encoded.release();

            out.writeInt(bytes.length);
            o.write(bytes);
            o.flush();
            o.close();
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
