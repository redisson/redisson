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
import java.util.Arrays;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CacheKey implements Serializable {
    
    private static final long serialVersionUID = 5790732187795028243L;
    
    private final byte[] keyHash;

    public CacheKey(byte[] keyHash) {
        super();
        this.keyHash = keyHash;
    }

    public byte[] getKeyHash() {
        return keyHash;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(keyHash);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CacheKey other = (CacheKey) obj;
        if (!Arrays.equals(keyHash, other.keyHash))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "CacheKey [keyHash=" + Arrays.toString(keyHash) + "]";
    }
    
}
