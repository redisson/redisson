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

import java.util.Objects;

/**
 * Cached scored-sorted-set entry with pre-encoded value bytes.
 *
 * @param <T> value type
 */
public final class CachedSortedSetEntry<T> {

    public final double score;
    public final T value;
    public final byte[] encoded;

    public CachedSortedSetEntry(double score, T value, ByteBuf encoded) {
        this.score = score;
        this.value = value;
        try {
            this.encoded = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), this.encoded);
        } finally {
            encoded.release();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CachedSortedSetEntry)) {
            return false;
        }
        CachedSortedSetEntry<?> that = (CachedSortedSetEntry<?>) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}

