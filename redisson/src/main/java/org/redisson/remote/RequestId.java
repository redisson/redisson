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
package org.redisson.remote;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RequestId {

    private final long id0;
    private final long id1;
    
    public RequestId(String id) {
        this(ByteBufUtil.decodeHexDump(id));
    }
    
    public RequestId(byte[] buf) {
        ByteBuf b = Unpooled.wrappedBuffer(buf);
        try {
            id0 = b.readLong();
            id1 = b.readLong();
        } finally {
            b.release();
        }
    }
    
    @Override
    public String toString() {
        ByteBuf id = Unpooled.buffer(16);
        try {
            id.writeLong(id0);
            id.writeLong(id1);
            return ByteBufUtil.hexDump(id);
        } finally {
            id.release();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (id0 ^ (id0 >>> 32));
        result = prime * result + (int) (id1 ^ (id1 >>> 32));
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
        if (id0 != other.id0)
            return false;
        if (id1 != other.id1)
            return false;
        return true;
    }

    
}
