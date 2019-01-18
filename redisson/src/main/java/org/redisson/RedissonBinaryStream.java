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
package org.redisson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.redisson.api.RBinaryStream;
import org.redisson.api.RFuture;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBinaryStream extends RedissonBucket<byte[]> implements RBinaryStream {

    class RedissonOutputStream extends OutputStream {
        
        @Override
        public void write(int b) throws IOException {
            writeBytes(new byte[] {(byte)b});
        }
        
        private void writeBytes(byte[] bytes) {
            get(writeAsync(bytes));
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            byte[] dest;
            if (b.length == len && off == 0) {
                dest = b;
            } else {
                dest = new byte[len];
                System.arraycopy(b, off, dest, 0, len);
            }
            writeBytes(dest);
        }
        
    }
    
    public class RedissonInputStream extends InputStream {

        private int index;
        private int mark;
        
        public void seek(long pos) {
            if (pos >= 0 && pos < size()) {
                index = (int) pos;
            } else {
                throw new IllegalStateException("size is " + size() + " but pos is " + pos);
            }
        }
        
        @Override
        public long skip(long n) throws IOException {
            long k = size() - index;
            if (n < k) {
                k = n;
                if (n < 0) {
                    k = 0;
                }
            }

            index += k;
            return k;
        }
        
        @Override
        public void mark(int readlimit) {
            mark = index;
        }
        
        @Override
        public void reset() throws IOException {
            index = mark;
        }
        
        @Override
        public int available() throws IOException {
            return (int)(size() - index);
        }
        
        @Override
        public boolean markSupported() {
            return true;
        }
        
        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int len = read(b);
            if (len == -1) {
                return -1;
            }
            return b[0] & 0xff;
        }
        
        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (b == null) {
                throw new NullPointerException();
            }
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            
            return (Integer)get(commandExecutor.evalReadAsync(getName(), codec, new RedisCommand<Integer>("EVAL", new Decoder<Integer>() {
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
                            }),
                              "local parts = redis.call('get', KEYS[2]); "
                            + "if parts ~= false then "
                                + "local startPart = math.floor(tonumber(ARGV[1])/536870912); "
                                + "local endPart = math.floor(tonumber(ARGV[2])/536870912); "
                                + "local startPartName = KEYS[1]; "
                                + "local endPartName = KEYS[1]; "

                                + "if startPart > 0 then "
                                    + "startPartName = KEYS[1] .. ':' .. startPart; "
                                + "end; "
                                + "if endPart > 0 then "
                                    + "endPartName = KEYS[1] .. ':' .. endPart; "
                                + "end; "

                                + "if startPartName ~= endPartName then "
                                    + "local startIndex = tonumber(ARGV[1]) - startPart*536870912; "
                                    + "local endIndex = tonumber(ARGV[2]) - endPart*536870912; "
                                    + "local result = redis.call('getrange', startPartName, startIndex, 536870911); "
                                    + "result = result .. redis.call('getrange', endPartName, 0, endIndex-1); "
                                    + "return result; "
                                + "end; "
                                    
                                + "local startIndex = tonumber(ARGV[1]) - startPart*536870912; "
                                + "local endIndex = tonumber(ARGV[2]) - endPart*536870912; "
                                + "return redis.call('getrange', startPartName, startIndex, endIndex);"
                            + "end;"
                            + "return redis.call('getrange', KEYS[1], ARGV[1], ARGV[2]);",
              Arrays.<Object>asList(getName(), getPartsName()), index, index + len - 1));
        }

    }
    
    protected RedissonBinaryStream(CommandAsyncExecutor connectionManager, String name) {
        super(ByteArrayCodec.INSTANCE, connectionManager, name);
    }
    
    @Override
    public RFuture<Long> sizeAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_LONG,
                "local parts = redis.call('get', KEYS[2]); "
              + "local lastPartName = KEYS[1];"
              + "if parts ~= false then "
                  + "lastPartName = KEYS[1] .. ':' .. (tonumber(parts)-1);"
                  + "local lastPartSize = redis.call('strlen', lastPartName);"
                  + "return ((tonumber(parts)-1) * 536870912) + lastPartSize;"
              + "end;"
              + "return redis.call('strlen', lastPartName);",
          Arrays.<Object>asList(getName(), getPartsName()));
    }

    private RFuture<Void> writeAsync(byte[] bytes) {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_VOID,
                "local parts = redis.call('get', KEYS[2]); "
              + "local lastPartName = KEYS[1];"
              + "if parts ~= false then "
                  + "lastPartName = KEYS[1] .. ':' .. (tonumber(parts)-1);"
              + "end;"
              + "local lastPartSize = redis.call('strlen', lastPartName);"
              + "if lastPartSize == 0 then "
                  + "redis.call('append', lastPartName, ARGV[1]); "
                  + "return; "
              + "end;"
                  
              + "local chunkSize = 536870912 - lastPartSize; "
              + "local arraySize = string.len(ARGV[1]); "
              + "if chunkSize > 0 then "
                  + "if chunkSize >= arraySize then "
                      + "redis.call('append', lastPartName, ARGV[1]); "
                      + "return; "
                  + "else "
                      + "local chunk = string.sub(ARGV[1], 1, chunkSize);"
                      + "redis.call('append', lastPartName, chunk); "
                      
                      + "if parts == false then "
                          + "parts = 1;"
                          + "redis.call('incrby', KEYS[2], 2); "
                      + "else "
                          + "redis.call('incrby', KEYS[2], 1); "
                      + "end; "
                          
                      + "local newPartName = KEYS[1] .. ':' .. parts; " 
                      + "chunk = string.sub(ARGV[1], -(arraySize - chunkSize));"
                      + "redis.call('append', newPartName, chunk); "
                  + "end; "
              + "else "
                  + "if parts == false then "
                      + "parts = 1;"
                      + "redis.call('incrby', KEYS[2], 2); "
                  + "else "
                      + "redis.call('incrby', KEYS[2], 1); "
                  + "end; "
                  
                  + "local newPartName = KEYS[1] .. ':' .. parts; " 
                  + "local chunk = string.sub(ARGV[1], -(arraySize - chunkSize));"
                  + "redis.call('append', newPartName, ARGV[1]); "
              + "end; ",
          Arrays.<Object>asList(getName(), getPartsName()), bytes);
    }
    
    @Override
    public InputStream getInputStream() {
        return new RedissonInputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        return new RedissonOutputStream();
    }

    @Override
    public RFuture<Void> setAsync(byte[] value) {
        if (value.length > 512*1024*1024) {
            RPromise<Void> result = new RedissonPromise<Void>();
            int chunkSize = 10*1024*1024;
            write(value, result, chunkSize, 0);
            return result;
        }

        return super.setAsync(value);
    }

    private void write(final byte[] value, final RPromise<Void> result, final int chunkSize, final int i) {
        final int len = Math.min(value.length - i*chunkSize, chunkSize);
        byte[] bytes = Arrays.copyOfRange(value, i*chunkSize, i*chunkSize + len);
        writeAsync(bytes).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                int j = i + 1;
                if (j*chunkSize > value.length) {
                    result.trySuccess(null);
                } else {
                    write(value, result, chunkSize, j);
                }
            }
        });
    }
    
    private String getPartsName() {
        return suffixName(getName(), "parts");
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                "local parts = redis.call('get', KEYS[2]); "
              + "local names = {KEYS[1], KEYS[2]};"
              + "if parts ~= false then "
                  + "for i = 1, tonumber(parts)-1, 1 do "
                      + "table.insert(names, KEYS[1] .. ':' .. i); "
                  + "end; "
              + "end;"
              + "return redis.call('del', unpack(names));",
          Arrays.<Object>asList(getName(), getPartsName()));

    }
    
}
