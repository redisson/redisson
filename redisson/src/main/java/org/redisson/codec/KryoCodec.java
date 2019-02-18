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
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class KryoCodec extends BaseCodec {

    public interface KryoPool {

        Kryo get();

        void yield(Kryo kryo);
        
        ClassLoader getClassLoader();
        
        List<Class<?>> getClasses();

    }

    public static class KryoPoolImpl implements KryoPool {

        private final Queue<Kryo> objects = new ConcurrentLinkedQueue<Kryo>();
        private final List<Class<?>> classes;
        private final ClassLoader classLoader;

        public KryoPoolImpl(List<Class<?>> classes, ClassLoader classLoader) {
            this.classes = classes;
            this.classLoader = classLoader;
        }

        public Kryo get() {
            Kryo kryo = objects.poll();
            if (kryo == null) {
                kryo = createInstance();
            }
            return kryo;
        }

        public void yield(Kryo kryo) {
            objects.offer(kryo);
        }

        /**
         * Sub classes can customize the Kryo instance by overriding this method
         *
         * @return create Kryo instance
         */
        protected Kryo createInstance() {
            Kryo kryo = new Kryo();
            if (classLoader != null) {
                kryo.setClassLoader(classLoader);
            }
            kryo.setReferences(false);
            for (Class<?> clazz : classes) {
                kryo.register(clazz);
            }
            return kryo;
        }

        public List<Class<?>> getClasses() {
            return classes;
        }
        
        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

    }

    public class RedissonKryoCodecException extends RuntimeException {

        private static final long serialVersionUID = 9172336149805414947L;

        public RedissonKryoCodecException(Throwable cause) {
            super(cause.getMessage(), cause);
            setStackTrace(cause.getStackTrace());
        }
    }

    private final KryoPool kryoPool;

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            Kryo kryo = null;
            try {
                kryo = kryoPool.get();
                return kryo.readClassAndObject(new Input(new ByteBufInputStream(buf)));
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RedissonKryoCodecException(e);
            } finally {
                if (kryo != null) {
                    kryoPool.yield(kryo);
                }
            }
        }
    };

    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            Kryo kryo = null;
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream baos = new ByteBufOutputStream(out);
                Output output = new Output(baos);
                kryo = kryoPool.get();
                kryo.writeClassAndObject(output, in);
                output.close();
                return baos.buffer();
            } catch (Exception e) {
                out.release();
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RedissonKryoCodecException(e);
            } finally {
                if (kryo != null) {
                    kryoPool.yield(kryo);
                }
            }
        }
    };

    public KryoCodec() {
        this(Collections.<Class<?>>emptyList());
    }

    public KryoCodec(ClassLoader classLoader) {
        this(Collections.<Class<?>>emptyList(), classLoader);
    }
    
    public KryoCodec(ClassLoader classLoader, KryoCodec codec) {
        this(codec.kryoPool.getClasses(), classLoader);
    }
    
    public KryoCodec(List<Class<?>> classes) {
        this(classes, null);
    }

    public KryoCodec(List<Class<?>> classes, ClassLoader classLoader) {
        this(new KryoPoolImpl(classes, classLoader));
    }

    public KryoCodec(KryoPool kryoPool) {
        this.kryoPool = kryoPool;
    }

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
        if (kryoPool.getClassLoader() != null) {
            return kryoPool.getClassLoader();
        }
        return super.getClassLoader();
    }

}
