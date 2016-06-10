/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.misc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;
import net.openhft.hashing.LongHashFunction;

public class Hash {

    private Hash() {
    }
    
    public static String hashToBase64(byte[] objectState) {
        long h1 = LongHashFunction.farmUo().hashBytes(objectState);
        long h2 = LongHashFunction.xx_r39().hashBytes(objectState);

        ByteBuf buf = Unpooled.buffer((2 * Long.SIZE) / Byte.SIZE).writeLong(h1).writeLong(h2);

        ByteBuf b = Base64.encode(buf);
        String s = b.toString(CharsetUtil.UTF_8);
        b.release();
        buf.release();
        return s.substring(0, s.length() - 2);
    }
    
}
