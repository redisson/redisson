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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class SerializationCodec extends BaseCodec {

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            try {
                //set thread context class loader to be the classLoader variable as there could be reflection
                //done while reading from input stream which reflection will use thread class loader to load classes on demand
                ClassLoader currentThreadClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    ByteBufInputStream in = new ByteBufInputStream(buf);
                    ObjectInputStream inputStream;
                    if (classLoader != null) {
                        Thread.currentThread().setContextClassLoader(classLoader);
                        inputStream = new CustomObjectInputStream(classLoader, in);
                    } else {
                        inputStream = new ObjectInputStream(in);
                    }
                    return inputStream.readObject();
                } finally {
                    Thread.currentThread().setContextClassLoader(currentThreadClassLoader);
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    };

    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream result = new ByteBufOutputStream(out);
                ObjectOutputStream outputStream = new ObjectOutputStream(result);
                outputStream.writeObject(in);
                outputStream.close();
                return result.buffer();
            } catch (IOException e) {
                out.release();
                throw e;
            }
        }
    };
    
    private final ClassLoader classLoader;

    public SerializationCodec() {
        this(null);
    }
    
    public SerializationCodec(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public SerializationCodec(ClassLoader classLoader, SerializationCodec codec) {
        this.classLoader = classLoader;
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
        if (classLoader != null) {
            return classLoader;
        }
        return getClass().getClassLoader();
    }

}
