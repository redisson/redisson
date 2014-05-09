/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class KryoCodec implements RedissonCodec {

    public interface KryoPool {

        Kryo get();

        void yield(Kryo kryo);

    }

    public static class KryoPoolImpl implements KryoPool {

        private final Queue<Kryo> objects = new ConcurrentLinkedQueue<Kryo>();

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

    public KryoCodec() {
        this(new KryoPoolImpl());
    }

    public KryoCodec(KryoPool kryoPool) {
        this.kryoPool = kryoPool;
    }

    private Object decode(ByteBuffer bytes) {
        Kryo kryo = null;
        try {
            kryo = kryoPool.get();
            return kryo.readClassAndObject(new Input(new ByteArrayInputStream(bytes.array(), bytes
                    .arrayOffset() + bytes.position(), bytes.limit())));
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

    @Override
    public byte[] encodeValue(Object value) {
        Kryo kryo = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Output output = new Output(baos);
            kryo = kryoPool.get();
            kryo.writeClassAndObject(output, value);
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

    @Override
    public byte[] encodeKey(Object key) {
        return encodeValue(key);
    }

    @Override
    public Object decodeKey(ByteBuffer bytes) {
        return decode(bytes);
    }

    @Override
    public Object decodeValue(ByteBuffer bytes) {
        return decode(bytes);
    }

    @Override
    public byte[] encodeMapValue(Object value) {
        return encodeValue(value);
    }

    @Override
    public byte[] encodeMapKey(Object key) {
        return encodeKey(key);
    }

    @Override
    public Object decodeMapValue(ByteBuffer bytes) {
        return decodeValue(bytes);
    }

    @Override
    public Object decodeMapKey(ByteBuffer bytes) {
        return decodeKey(bytes);
    }

}
