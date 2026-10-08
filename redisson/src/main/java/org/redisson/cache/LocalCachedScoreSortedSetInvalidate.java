/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
@SuppressWarnings("serial")
public class LocalCachedScoreSortedSetInvalidate implements Serializable {

    public static class Entry {

        private final byte[] value;

        public Entry(byte[] value) {
            this.value = value;
        }

        public Entry(ByteBuf valueBuf) {
            value = new byte[valueBuf.readableBytes()];
            valueBuf.getBytes(valueBuf.readerIndex(), value);
        }

        public byte[] getValue() {
            return value;
        }

    }

    private List<Entry> entries = new ArrayList<>();

    private byte[] excludedId;

    public LocalCachedScoreSortedSetInvalidate() {
    }

    public LocalCachedScoreSortedSetInvalidate(byte[] excludedId, List<Entry> entries) {
        super();
        this.excludedId = excludedId;
        this.entries = entries;
    }

    public LocalCachedScoreSortedSetInvalidate(byte[] excludedId, ByteBuf valueBuf) {
        this.excludedId = excludedId;
        byte[] value = new byte[valueBuf.readableBytes()];
        valueBuf.getBytes(valueBuf.readerIndex(), value);
        entries = Collections.singletonList(new Entry(value));
    }

    public LocalCachedScoreSortedSetInvalidate(byte[] value) {
        entries = Collections.singletonList(new Entry(value));
    }

    public Collection<Entry> getEntries() {
        return entries;
    }

    public byte[] getExcludedId() {
        return excludedId;
    }
}
