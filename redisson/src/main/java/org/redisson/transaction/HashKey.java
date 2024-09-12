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
package org.redisson.transaction;

import org.redisson.client.codec.Codec;

import java.util.Objects;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public final class HashKey {

    final Codec codec;
    final String name;
    
    public HashKey(String name, Codec codec) {
        this.name = name;
        this.codec = codec;
    }
    
    public Codec getCodec() {
        return codec;
    }
    
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HashKey hashKey = (HashKey) o;
        return Objects.equals(name, hashKey.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
