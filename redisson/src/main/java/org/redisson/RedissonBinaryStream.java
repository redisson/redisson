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
package org.redisson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.redisson.api.RBinaryStream;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBinaryStream extends RedissonBucket<byte[]> implements RBinaryStream {

    class RedissonOutputStream extends OutputStream {
        
        @Override
        public void write(int b) throws IOException {
            get(commandExecutor.writeAsync(getName(), codec, RedisCommands.APPEND, getName(), new byte[] {(byte)b}));
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            byte[] dest = new byte[len];
            System.arraycopy(b, off, dest, 0, len);
            get(commandExecutor.writeAsync(getName(), codec, RedisCommands.APPEND, getName(), dest));
        }
        
    }
    
    class RedissonInputStream extends InputStream {

        private int index;
        
        @Override
        public int read() throws IOException {
            byte[] result = (byte[])get(commandExecutor.readAsync(getName(), codec, RedisCommands.GETRANGE, getName(), index, index));
            if (result.length == 0) {
                return -1;
            }
            index++;
            return result[0];
        }
        
        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            }
            
            return (Integer)get(commandExecutor.readAsync(getName(), codec, new RedisCommand<Integer>("GETRANGE", new Decoder<Integer>() {
                @Override
                public Integer decode(ByteBuf buf, State state) {
                    if (buf.readableBytes() == 0) {
                        return -1;
                    }
                    int readBytes = Math.min(buf.readableBytes(), len);
                    buf.readBytes(b, off, readBytes);
                    index += readBytes;
                    return readBytes;
                }
            }), getName(), index, index + b.length - 1));
        }
        
    }
    
    protected RedissonBinaryStream(CommandAsyncExecutor connectionManager, String name) {
        super(ByteArrayCodec.INSTANCE, connectionManager, name);
    }

    @Override
    public InputStream getInputStream() {
        return new RedissonInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        return new RedissonOutputStream();
    }

}
