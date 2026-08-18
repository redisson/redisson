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
import io.netty.buffer.ByteBufOutputStream;
import org.apache.fory.exception.ForyException;
import org.apache.fory.reflect.TypeRef;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.time.DateTimeException;
import java.util.Collections;
import java.util.Set;

/**
 * <a href="https://fory.apache.org">Apache Fory</a> JSON Type codec. Doesn't include `@class` field
 * during data encoding, and doesn't require it for data decoding.
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
public class TypedJsonForyCodec extends JsonForyCodec {

    private final Encoder encoder = new Encoder() {
        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            boolean written = false;
            try {
                writeJson(in, new ByteBufOutputStream(out));
                written = true;
                return out;
            } catch (ForyException | IllegalArgumentException | DateTimeException e) {
                throw new IOException("Unable to write the value", e);
            } finally {
                // releases the buffer on an Error too, which Apache Fory raises on a self-referencing value
                if (!written) {
                    out.release();
                }
            }
        }
    };

    private final Decoder<Object> valueDecoder;
    private final Decoder<Object> mapKeyDecoder;
    private final Decoder<Object> mapValueDecoder;

    private final TypeRef<?> valueTypeRef;
    private final TypeRef<?> mapKeyTypeRef;
    private final TypeRef<?> mapValueTypeRef;

    private final Class<?> valueClass;
    private final Class<?> mapKeyClass;
    private final Class<?> mapValueClass;

    public TypedJsonForyCodec(Class<?> valueClass) {
        this(null, null, null,
                valueClass, null, null, null, Collections.emptySet());
    }

    public TypedJsonForyCodec(Class<?> mapKeyClass, Class<?> mapValueClass) {
        this(null, null, null,
                null, mapKeyClass, mapValueClass, null, Collections.emptySet());
    }

    public TypedJsonForyCodec(Class<?> valueClass, Class<?> mapKeyClass, Class<?> mapValueClass) {
        this(null, null, null,
                valueClass, mapKeyClass, mapValueClass, null, Collections.emptySet());
    }

    public TypedJsonForyCodec(TypeRef<?> valueTypeRef) {
        this(valueTypeRef, null, null,
                null, null, null, null, Collections.emptySet());
    }

    public TypedJsonForyCodec(TypeRef<?> mapKeyTypeRef, TypeRef<?> mapValueTypeRef) {
        this(null, mapKeyTypeRef, mapValueTypeRef,
                null, null, null, null, Collections.emptySet());
    }

    public TypedJsonForyCodec(TypeRef<?> valueTypeRef, TypeRef<?> mapKeyTypeRef, TypeRef<?> mapValueTypeRef) {
        this(valueTypeRef, mapKeyTypeRef, mapValueTypeRef,
                null, null, null, null, Collections.emptySet());
    }

    public TypedJsonForyCodec(ClassLoader classLoader, TypedJsonForyCodec codec) {
        this(codec.valueTypeRef, codec.mapKeyTypeRef, codec.mapValueTypeRef,
                codec.valueClass, codec.mapKeyClass, codec.mapValueClass,
                classLoader, codec.allowedClasses);
    }

    protected TypedJsonForyCodec(TypeRef<?> valueTypeRef, TypeRef<?> mapKeyTypeRef, TypeRef<?> mapValueTypeRef,
                                 Class<?> valueClass, Class<?> mapKeyClass, Class<?> mapValueClass,
                                 ClassLoader classLoader, Set<String> allowedClasses) {
        super(classLoader, allowedClasses);

        this.valueClass = valueClass;
        this.valueTypeRef = valueTypeRef;
        this.mapKeyClass = mapKeyClass;
        this.mapKeyTypeRef = mapKeyTypeRef;
        this.mapValueClass = mapValueClass;
        this.mapValueTypeRef = mapValueTypeRef;

        this.valueDecoder = createDecoder(valueClass, valueTypeRef);
        this.mapKeyDecoder = createDecoder(mapKeyClass, mapKeyTypeRef);
        this.mapValueDecoder = createDecoder(mapValueClass, mapValueTypeRef);
    }

    private Decoder<Object> createDecoder(Class<?> type, TypeRef<?> typeRef) {
        return (buf, state) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            try {
                if (type != null) {
                    return getForyJson().fromJson(data, type);
                }
                if (typeRef != null) {
                    return getForyJson().fromJson(data, typeRef);
                }
                return getForyJson().fromJson(data, Object.class);
            } catch (ForyException | IllegalArgumentException | DateTimeException e) {
                throw new IOException("Unable to read the stored value", e);
            }
        };
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return valueDecoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return mapKeyDecoder;
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return encoder;
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return mapValueDecoder;
    }

    @Override
    public Encoder getMapValueEncoder() {
        return encoder;
    }

}
