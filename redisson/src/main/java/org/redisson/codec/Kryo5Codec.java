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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.esotericsoftware.kryo.util.Pool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

import static com.esotericsoftware.kryo.util.Util.className;

/**
 * Kryo 5 codec
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
public class Kryo5Codec extends BaseCodec {

    private static final Logger logger = LoggerFactory.getLogger(Kryo5Codec.class);
    private static final List<String> MISSED_COLLECTION_CLASSES = Arrays.asList("Unmodifiable", "Synchronized", "Checked");

    private static final class SimpleInstantiatorStrategy implements org.objenesis.strategy.InstantiatorStrategy {

        private final StdInstantiatorStrategy ss = new StdInstantiatorStrategy();

        @Override
        public <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> type) {
            // Reflection.
            try {
                Constructor ctor;
                try {
                    ctor = type.getConstructor((Class[]) null);
                } catch (Exception ex) {
                    ctor = type.getDeclaredConstructor((Class[]) null);
                    ctor.setAccessible(true);
                }
                final Constructor constructor = ctor;
                return new ObjectInstantiator() {
                    public Object newInstance() {
                        try {
                            return constructor.newInstance();
                        } catch (Exception ex) {
                            throw new KryoException("Error constructing instance of class: " + className(type), ex);
                        }
                    }
                };
            } catch (Exception ignored) {
            }

            return ss.newInstantiatorOf(type);
        }
    }

    private final Pool<Kryo> kryoPool;
    private final Pool<Input> inputPool;
    private final Pool<Output> outputPool;
    private final Set<String> allowedClasses;
    private final boolean useReferences;

    public Kryo5Codec() {
        this(null, Collections.emptySet(), false);
    }

    public Kryo5Codec(Set<String> allowedClasses, boolean useReferences) {
        this(null, allowedClasses, useReferences);
    }

    public Kryo5Codec(ClassLoader classLoader, Kryo5Codec codec) {
        this(classLoader, codec.allowedClasses, codec.useReferences);
    }

    public Kryo5Codec(ClassLoader classLoader) {
        this(classLoader, Collections.emptySet(), false);
    }

    public Kryo5Codec(ClassLoader classLoader, Set<String> allowedClasses, boolean useReferences) {
        this.allowedClasses = allowedClasses;
        this.useReferences = useReferences;

        this.kryoPool = new Pool<Kryo>(true, false, 1024) {
            @Override
            protected Kryo create() {
                try {
                    return createKryo(classLoader, useReferences);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        };

        this.inputPool = new Pool<Input>(true, false, 512) {
            @Override
            protected Input create() {
                return new Input(8192);
            }
        };

        this.outputPool = new Pool<Output>(true, false, 512) {
            @Override
            protected Output create() {
                return new Output(8192, -1);
            }
        };
    }

    protected Kryo createKryo(ClassLoader classLoader, boolean useReferences) throws ClassNotFoundException {
        Kryo kryo = new Kryo();
        if (classLoader != null) {
            kryo.setClassLoader(classLoader);
        }
        kryo.setInstantiatorStrategy(new SimpleInstantiatorStrategy());
        kryo.setRegistrationRequired(!allowedClasses.isEmpty());
        kryo.setReferences(useReferences);

        for (String allowedClass : allowedClasses) {
            kryo.register(Class.forName(allowedClass));
        }

        try {
            Class<?>[] f = Collections.class.getDeclaredClasses();
            Arrays.stream(f)
                    .filter(cls -> MISSED_COLLECTION_CLASSES.stream().anyMatch(s -> cls.getName().contains(s)))
                    .forEach(cls -> {
                        kryo.addDefaultSerializer(cls, new JavaSerializer());
                    });
        } catch (Exception e) {
            logger.warn("Unable to register Collections serializer", e);
        }
        kryo.addDefaultSerializer(EnumMap.class, new JavaSerializer());
        kryo.addDefaultSerializer(Throwable.class, new JavaSerializer());
        kryo.addDefaultSerializer(UUID.class, new DefaultSerializers.UUIDSerializer());
        kryo.addDefaultSerializer(URI.class, new DefaultSerializers.URISerializer());
        kryo.addDefaultSerializer(Pattern.class, new DefaultSerializers.PatternSerializer());
        return kryo;
    }

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            Kryo kryo = kryoPool.obtain();
            Input input = inputPool.obtain();
            try {
                input.setInputStream(new ByteBufInputStream(buf));
                return kryo.readClassAndObject(input);
            } finally {
                kryoPool.free(kryo);
                inputPool.free(input);
            }
        }
    };

    private final Encoder encoder = new Encoder() {
        @Override
        @SuppressWarnings("IllegalCatch")
        public ByteBuf encode(Object in) throws IOException {
            Kryo kryo = kryoPool.obtain();
            Output output = outputPool.obtain();
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream baos = new ByteBufOutputStream(out);
                output.setOutputStream(baos);
                kryo.writeClassAndObject(output, in);
                output.flush();
                return baos.buffer();
            } catch (RuntimeException e) {
                out.release();
                throw e;
            } finally {
                kryoPool.free(kryo);
                outputPool.free(output);
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
