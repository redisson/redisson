/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import io.netty.util.concurrent.FastThreadLocal;
import org.jboss.marshalling.*;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.util.Locale;

/**
 * JBoss Marshalling codec.
 * 
 * Uses River protocol by default.
 * 
 * https://github.com/jboss-remoting/jboss-marshalling
 * 
 * @author Nikita Koksharov
 *
 */
@Deprecated
public class MarshallingCodec extends BaseCodec {

    private final FastThreadLocal<Unmarshaller> decoderThreadLocal = new FastThreadLocal<Unmarshaller>() {
        @Override
        protected Unmarshaller initialValue() throws IOException {
            return factory.createUnmarshaller(configuration);
        };
    };
    
    private final FastThreadLocal<Marshaller> encoderThreadLocal = new FastThreadLocal<Marshaller>() {
        @Override
        protected Marshaller initialValue() throws IOException {
            return factory.createMarshaller(configuration);
        };
    };
    
    public static class ByteInputWrapper implements ByteInput {

        private final ByteBuf byteBuf;
        
        public ByteInputWrapper(ByteBuf byteBuf) {
            super();
            this.byteBuf = byteBuf;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public int read() throws IOException {
            return byteBuf.readByte() & 0xff;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int length = available();
            int readLength = Math.min(len, length);
            byteBuf.readBytes(b, off, readLength);
            return readLength;
        }

        @Override
        public int available() throws IOException {
            return byteBuf.readableBytes();
        }

        @Override
        public long skip(long n) throws IOException {
            int length = available();
            long skipLength = Math.min(length, n);
            byteBuf.readerIndex((int) (byteBuf.readerIndex() + skipLength));
            return skipLength;
        }
        
    }
    
    public static class ByteOutputWrapper implements ByteOutput {

        private final ByteBuf byteBuf;
        
        public ByteOutputWrapper(ByteBuf byteBuf) {
            this.byteBuf = byteBuf;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void write(int b) throws IOException {
            byteBuf.writeByte(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            byteBuf.writeBytes(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            byteBuf.writeBytes(b, off, len);
        }
        
    }
    
    public enum Protocol {
        
        SERIAL,
        
        RIVER
        
    }
    
    private final Decoder<Object> decoder = (buf, state) -> {
        Unmarshaller unmarshaller = decoderThreadLocal.get();
        try {
            unmarshaller.start(new ByteInputWrapper(buf));
            return unmarshaller.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        } finally {
            unmarshaller.finish();
            unmarshaller.close();
        }
    };
    
    private final Encoder encoder = in -> {
        ByteBuf out = ByteBufAllocator.DEFAULT.buffer();

        Marshaller marshaller = encoderThreadLocal.get();
        try {
            marshaller.start(new ByteOutputWrapper(out));
            marshaller.writeObject(in);
        } catch (IOException e) {
            marshaller.finish();
            marshaller.close();
            out.release();
            throw e;
        } catch (Exception e) {
            marshaller.finish();
            marshaller.close();
            out.release();
            throw new IOException(e);
        }
        marshaller.finish();
        marshaller.close();
        return out;
    };
    
    private final MarshallerFactory factory;
    private final MarshallingConfiguration configuration;
    private ClassLoader classLoader;

    protected MarshallingConfiguration createConfig() {
        MarshallingConfiguration config = new MarshallingConfiguration();
        config.setInstanceCount(32);
        config.setClassCount(16);
        return config;
    }

    public MarshallingCodec() {
        this(MarshallingCodec.class.getClassLoader());
    }
    
    public MarshallingCodec(ClassLoader classLoader) {
        this(Protocol.RIVER, null);
        configuration.setClassResolver(new SimpleClassResolver(classLoader));
        this.classLoader = classLoader;
        warmup();
    }
    
    public MarshallingCodec(ClassLoader classLoader, MarshallingCodec codec) {
        this.factory = codec.factory;
        MarshallingConfiguration config = new MarshallingConfiguration();
        config.setBufferSize(codec.configuration.getBufferSize());
        config.setClassCount(codec.configuration.getClassCount());
        config.setClassExternalizerFactory(codec.configuration.getClassExternalizerFactory());
        config.setClassResolver(new SimpleClassResolver(classLoader));
        config.setClassTable(codec.configuration.getClassTable());
        config.setExceptionListener(codec.configuration.getExceptionListener());
        config.setObjectPreResolver(codec.configuration.getObjectPreResolver());
        config.setObjectResolver(codec.configuration.getObjectResolver());
        config.setSerializabilityChecker(codec.configuration.getSerializabilityChecker());
        config.setVersion(codec.configuration.getVersion());
        this.configuration = config;
        this.classLoader = classLoader;
        warmup();
    }
    
    public MarshallingCodec(Protocol protocol, MarshallingConfiguration configuration) {
        this.factory = Marshalling.getProvidedMarshallerFactory(protocol.toString().toLowerCase(Locale.ENGLISH));
        if (factory == null) {
            throw new IllegalArgumentException(protocol.toString());
        }
        if (configuration == null) {
            configuration = createConfig();
        }
        this.configuration = configuration;
        warmup();
    }

    private static boolean warmedup = false;

    private void warmup() {
        if (warmedup) {
            return;
        }
        warmedup = true;

        try {
            ByteBuf d = getValueEncoder().encode("testValue");
            getValueDecoder().decode(d, null);
            d.release();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        if (this.classLoader != null) {
            return classLoader;
        }

        return super.getClassLoader();
    }
    
}
