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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@SuppressWarnings("serial")
public class LocalCachedMapUpdate implements Serializable {

    public static class Entry {
        
        private final byte[] key;
        private final byte[] value;
        
        public Entry(byte[] key, byte[] value) {
            this.key = key;
            this.value = value;
        }
        
        public Entry(ByteBuf keyBuf, ByteBuf valueBuf) {
            key = new byte[keyBuf.readableBytes()];
            keyBuf.getBytes(keyBuf.readerIndex(), key);
            
            value = new byte[valueBuf.readableBytes()];
            valueBuf.getBytes(valueBuf.readerIndex(), value);
        }

        public byte[] getKey() {
            return key;
        }
        
        public byte[] getValue() {
            return value;
        }
        
    }
    
    private List<Entry> entries = new ArrayList<Entry>();

    public LocalCachedMapUpdate() {
    }
    
    public LocalCachedMapUpdate(List<Entry> entries) {
        super();
        this.entries = entries;
    }

    public LocalCachedMapUpdate(ByteBuf keyBuf, ByteBuf valueBuf) {
        byte[] key = new byte[keyBuf.readableBytes()];
        keyBuf.getBytes(keyBuf.readerIndex(), key);
        
        byte[] value = new byte[valueBuf.readableBytes()];
        valueBuf.getBytes(valueBuf.readerIndex(), value);
        entries = Collections.singletonList(new Entry(key, value));
    }
    
    public LocalCachedMapUpdate(byte[] key, byte[] value) {
        entries = Collections.singletonList(new Entry(key, value));
    }

    public Collection<Entry> getEntries() {
        return entries;
    }

}
