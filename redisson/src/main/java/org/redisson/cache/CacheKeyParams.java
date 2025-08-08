/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.misc.Hash;

import java.util.Arrays;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class CacheKeyParams {

    private final Object[] values;

    public CacheKeyParams(Object[] values) {
        this.values = Arrays.stream(values)
                .map(value -> deepConvertByteBuf(value))
                .toArray();
    }

    private Object deepConvertByteBuf(Object obj) {
        if (obj instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) obj;
            return Hash.hash128(buf);
        } else if (obj instanceof Object[]) {
            return Arrays.stream((Object[]) obj)
                    .map(value -> this.deepConvertByteBuf(value))
                    .toArray();
        }
        return obj;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKeyParams cacheKey = (CacheKeyParams) o;
        return Arrays.deepEquals(values, cacheKey.values);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(values);
    }

}
