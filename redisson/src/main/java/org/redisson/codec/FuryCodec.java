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
import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.FuryBuilder;
import org.apache.fury.config.Language;
import org.apache.fury.io.FuryStreamReader;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.memory.MemoryUtils;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * <a href="https://github.com/apache/fury">Apache Fury</a> codec
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
@Deprecated
public class FuryCodec extends BaseCodec {

    static final Logger log = LoggerFactory.getLogger(FuryCodec.class);

    private final ThreadSafeFury fury;
    private final Set<String> allowedClasses;
    private final Language language;

    public FuryCodec() {
        this(null, Collections.emptySet(), Language.JAVA);
    }

    public FuryCodec(Set<String> allowedClasses) {
        this(null, allowedClasses, Language.JAVA);
    }

    public FuryCodec(Language language) {
        this(null, Collections.emptySet(), language);
    }

    public FuryCodec(Set<String> allowedClasses, Language language) {
        this(null, allowedClasses, language);
    }

    public FuryCodec(ClassLoader classLoader, FuryCodec codec) {
        this(classLoader, codec.allowedClasses, codec.language);
        log.warn("FuryCodec is deprecated and will be removed in future. Use ForyCodec instead.");
    }

    public FuryCodec(ClassLoader classLoader) {
        this(classLoader, Collections.emptySet(), Language.JAVA);
    }

    public FuryCodec(ClassLoader classLoader, Set<String> allowedClasses, Language language) {
        this.allowedClasses = allowedClasses;
        this.language = language;

        FuryBuilder builder = Fury.builder();
        if (classLoader != null) {
            builder.withClassLoader(classLoader);
        }
        builder.withLanguage(language);
        builder.requireClassRegistration(!allowedClasses.isEmpty());
        fury = builder.buildThreadSafeFuryPool(10, 512);

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
                return fury.deserialize(FuryStreamReader.of(new ByteBufInputStream(buf)));
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
