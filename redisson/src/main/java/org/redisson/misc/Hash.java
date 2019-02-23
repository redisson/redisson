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
package org.redisson.misc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public final class Hash {

    private static final long[] KEY = {0x9e3779b97f4a7c15L, 0xf39cc0605cedc834L, 0x1082276bf3a27251L, 0xf86c6a11d0c18e95L};

    private Hash() {
    }
    
    public static byte[] hash128toArray(ByteBuf objectState) {
        long[] hash = hash128(objectState);

        ByteBuf buf = Unpooled.copyLong(hash[0], hash[1]);
        try {
            byte[] dst = new byte[buf.readableBytes()];
            buf.readBytes(dst);
            return dst;
        } finally {
            buf.release();
        }
    }
    
    public static long hash64(ByteBuf objectState) {
        HighwayHash h = calcHash(objectState);
        return h.finalize64();
    }
    
    public static long[] hash128(ByteBuf objectState) {
        HighwayHash h = calcHash(objectState);
        return h.finalize128();
    }

    protected static HighwayHash calcHash(ByteBuf objectState) {
        HighwayHash h = new HighwayHash(KEY);
        int i;
        int length = objectState.readableBytes();
        int offset = objectState.readerIndex();
        byte[] data = new byte[32];
        for (i = 0; i + 32 <= length; i += 32) {
            objectState.getBytes(offset  + i, data);
            h.updatePacket(data, 0);
        }
        if ((length & 31) != 0) {
            data = new byte[length & 31];
            objectState.getBytes(offset  + i, data);
            h.updateRemainder(data, 0, length & 31);
        }
        return h;
    }

    public static String hash128toBase64(ByteBuf objectState) {
        long[] hash = hash128(objectState);

        ByteBuf buf = Unpooled.copyLong(hash[0], hash[1]);
        try {
            ByteBuf b = Base64.encode(buf);
            try {
                String s = b.toString(CharsetUtil.UTF_8);
                return s.substring(0, s.length() - 2);
            } finally {
                b.release();
            }
        } finally {
            buf.release();
        }
    }
    
}
