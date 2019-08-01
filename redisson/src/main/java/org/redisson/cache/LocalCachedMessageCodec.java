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
package org.redisson.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LocalCachedMessageCodec extends BaseCodec {

    public static final LocalCachedMessageCodec INSTANCE = new LocalCachedMessageCodec();
    
    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            byte type = buf.readByte();
            if (type == 0x0) {
                byte[] id = new byte[16];
                buf.readBytes(id);
                return new LocalCachedMapClear(id);
            }
            
            if (type == 0x1) {
                byte[] excludedId = new byte[16];
                buf.readBytes(excludedId);
                int hashesCount = buf.readInt();
                byte[][] hashes = new byte[hashesCount][];
                for (int i = 0; i < hashesCount; i++) {
                    byte[] keyHash = new byte[16];
                    buf.readBytes(keyHash);
                    hashes[i] = keyHash;
                }
                return new LocalCachedMapInvalidate(excludedId, hashes);
            }
            
            if (type == 0x2) {
                List<LocalCachedMapUpdate.Entry> entries = new ArrayList<LocalCachedMapUpdate.Entry>();
                while (true) {
                    int keyLen = buf.readInt();
                    byte[] key = new byte[keyLen];
                    buf.readBytes(key);
                    int valueLen = buf.readInt();
                    byte[] value = new byte[valueLen];
                    buf.readBytes(value);
                    entries.add(new LocalCachedMapUpdate.Entry(key, value));
                    
                    if (!buf.isReadable()) {
                        break;
                    }
                }
                return new LocalCachedMapUpdate(entries);
            }
            
            if (type == 0x3) {
                byte len = buf.readByte();
                CharSequence requestId = buf.readCharSequence(len, CharsetUtil.US_ASCII);
                long timeout = buf.readLong();
                int hashesCount = buf.readInt();
                byte[][] hashes = new byte[hashesCount][];
                for (int i = 0; i < hashesCount; i++) {
                    byte[] keyHash = new byte[16];
                    buf.readBytes(keyHash);
                    hashes[i] = keyHash;
                }
                return new LocalCachedMapDisable(requestId.toString(), hashes, timeout);
            }
            
            if (type == 0x4) {
                return new LocalCachedMapDisableAck();
            }

            if (type == 0x5) {
                byte len = buf.readByte();
                CharSequence requestId = buf.readCharSequence(len, CharsetUtil.UTF_8);
                int hashesCount = buf.readInt();
                byte[][] hashes = new byte[hashesCount][];
                for (int i = 0; i < hashesCount; i++) {
                    byte[] keyHash = new byte[16];
                    buf.readBytes(keyHash);
                    hashes[i] = keyHash;
                }
                return new LocalCachedMapEnable(requestId.toString(), hashes);
            }

            throw new IllegalArgumentException("Can't parse packet");
        }
    };
    
    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            if (in instanceof LocalCachedMapClear) {
                LocalCachedMapClear li = (LocalCachedMapClear) in; 
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer(1);
                result.writeByte(0x0);
                result.writeBytes(li.getRequestId());
                return result;
            }
            if (in instanceof LocalCachedMapInvalidate) {
                LocalCachedMapInvalidate li = (LocalCachedMapInvalidate) in;
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer();
                result.writeByte(0x1);
                
                result.writeBytes(li.getExcludedId());
                result.writeInt(li.getKeyHashes().length);
                for (int i = 0; i < li.getKeyHashes().length; i++) {
                    result.writeBytes(li.getKeyHashes()[i]);
                }
                return result;
            }
            
            if (in instanceof LocalCachedMapUpdate) {
                LocalCachedMapUpdate li = (LocalCachedMapUpdate) in;
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer();
                result.writeByte(0x2);

                for (LocalCachedMapUpdate.Entry e : li.getEntries()) {
                    result.writeInt(e.getKey().length);
                    result.writeBytes(e.getKey());
                    result.writeInt(e.getValue().length);
                    result.writeBytes(e.getValue());
                }
                return result;
            }
            
            if (in instanceof LocalCachedMapDisable) {
                LocalCachedMapDisable li = (LocalCachedMapDisable) in;
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer();
                result.writeByte(0x3);
                
                result.writeByte(li.getRequestId().length());
                result.writeCharSequence(li.getRequestId(), CharsetUtil.UTF_8);
                result.writeLong(li.getTimeout());
                result.writeInt(li.getKeyHashes().length);
                for (int i = 0; i < li.getKeyHashes().length; i++) {
                    result.writeBytes(li.getKeyHashes()[i]);
                }
                return result;
            }

            if (in instanceof LocalCachedMapDisableAck) {
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer(1);
                result.writeByte(0x4);
                return result;
            }

            if (in instanceof LocalCachedMapEnable) {
                LocalCachedMapEnable li = (LocalCachedMapEnable) in;
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer();
                result.writeByte(0x5);
                
                result.writeByte(li.getRequestId().length());
                result.writeCharSequence(li.getRequestId(), CharsetUtil.UTF_8);
                result.writeInt(li.getKeyHashes().length);
                for (int i = 0; i < li.getKeyHashes().length; i++) {
                    result.writeBytes(li.getKeyHashes()[i]);
                }
                return result;
            }

            throw new IllegalArgumentException("Can't encode packet " + in);
        }
    };


    public LocalCachedMessageCodec() {
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
