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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.config.Language;
import org.apache.fory.io.ForyStreamReader;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * <a href="https://github.com/apache/fory">Apache Fory</a> codec
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
public class ForyCodec extends BaseCodec {

    private final ThreadSafeFory fury;
    private final Set<String> allowedClasses;
    private final Language language;

    public ForyCodec() {
        this(null, Collections.emptySet(), Language.JAVA);
    }

    public ForyCodec(Set<String> allowedClasses) {
        this(null, allowedClasses, Language.JAVA);
    }

    public ForyCodec(Language language) {
        this(null, Collections.emptySet(), language);
    }

    public ForyCodec(Set<String> allowedClasses, Language language) {
        this(null, allowedClasses, language);
    }

    public ForyCodec(ClassLoader classLoader, ForyCodec codec) {
        this(classLoader, codec.allowedClasses, codec.language);
    }

    public ForyCodec(ClassLoader classLoader) {
        this(classLoader, Collections.emptySet(), Language.JAVA);
    }

    public ForyCodec(ClassLoader classLoader, Set<String> allowedClasses, Language language) {
        this.allowedClasses = allowedClasses;
        this.language = language;

        ForyBuilder builder = Fory.builder();
        if (classLoader != null) {
            builder.withClassLoader(classLoader);
        }
        builder.withLanguage(language);
        builder.requireClassRegistration(!allowedClasses.isEmpty());
        fury = builder.buildThreadSafeForyPool(10, 512);

        for (String allowedClass : allowedClasses) {
            try {
                fury.register(Class.forName(allowedClass));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            if (buf.nioBufferCount() == 1) {
                MemoryBuffer furyBuffer = MemoryUtils.wrap(buf.nioBuffer());
                try {
                    return fury.deserialize(furyBuffer);
                } finally {
                    buf.readerIndex(buf.readerIndex() + furyBuffer.readerIndex());
                }
            } else {
                return fury.deserialize(ForyStreamReader.of(new ByteBufInputStream(buf)));
            }
        }
    };

    private final Encoder encoder = new Encoder() {
        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            MemoryBuffer furyBuffer = null;
            int remainingSize = out.capacity() - out.writerIndex();
            if (out.hasArray()) {
                furyBuffer = MemoryUtils.wrap(out.array(), out.arrayOffset() + out.writerIndex(),
                  remainingSize);
            } else if (out.hasMemoryAddress()) {
                furyBuffer =  MemoryUtils.buffer(out.memoryAddress() + out.writerIndex(), remainingSize);
            }
            if (furyBuffer != null) {
                int size = furyBuffer.size();
                fury.serialize(furyBuffer, in);
                if (furyBuffer.size() > size) {
                    out.writeBytes(furyBuffer.getHeapMemory(), 0, furyBuffer.size());
                } else {
                    out.writerIndex(out.writerIndex() + furyBuffer.writerIndex());
                }
                return out;
            } else {
                try {
                    ByteBufOutputStream baos = new ByteBufOutputStream(out);
                    fury.serialize(baos, in);
                    return baos.buffer();
                } catch (Exception e) {
                    out.release();
                    throw e;
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
