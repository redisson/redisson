/**
 * Copyright 2016 Nikita Koksharov
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

public class KryoCodec implements Codec {

    public interface KryoPool {

        Kryo get();

        void yield(Kryo kryo);

    }

    public static class KryoPoolImpl implements KryoPool {

        private final Queue<Kryo> objects = new ConcurrentLinkedQueue<Kryo>();
        private final List<Class<?>> classes;

        public KryoPoolImpl(List<Class<?>> classes) {
            this.classes = classes;
        }

        public Kryo get() {
            Kryo kryo;
            if ((kryo = objects.poll()) == null) {
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
            kryo.setReferences(false);
            for (Class<?> clazz : classes) {
                kryo.register(clazz);
            }
            return kryo;
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
        public byte[] encode(Object in) throws IOException {
            Kryo kryo = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Output output = new Output(baos);
                kryo = kryoPool.get();
                kryo.writeClassAndObject(output, in);
                output.close();
                return baos.toByteArray();
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

    public KryoCodec() {
        this(new KryoPoolImpl(Collections.<Class<?>>emptyList()));
    }

    public KryoCodec(List<Class<?>> classes) {
        this(new KryoPoolImpl(classes));
    }


    public KryoCodec(KryoPool kryoPool) {
        this.kryoPool = kryoPool;
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return getValueDecoder();
    }

    @Override
    public Encoder getMapValueEncoder() {
        return getValueEncoder();
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return getValueDecoder();
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return getValueEncoder();
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

}
