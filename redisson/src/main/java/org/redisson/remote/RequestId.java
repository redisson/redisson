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
package org.redisson.remote;

import java.util.Arrays;

import io.netty.buffer.ByteBufUtil;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RequestId {

    private final byte[] id;
    
    public RequestId(String id) {
        this(ByteBufUtil.decodeHexDump(id));
    }
    
    public RequestId(byte[] buf) {
        id = buf;
    }
    
    @Override
    public String toString() {
        return ByteBufUtil.hexDump(id);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(id);
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
        RequestId other = (RequestId) obj;
        if (!Arrays.equals(id, other.id))
            return false;
        return true;
    }

}
