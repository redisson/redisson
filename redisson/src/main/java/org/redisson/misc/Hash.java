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
package org.redisson.misc;

import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;
import net.openhft.hashing.LongHashFunction;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class Hash {

    private Hash() {
    }

   
    public static byte[] hash(ByteBuf objectState) {
        ByteBuffer b = objectState.internalNioBuffer(objectState.readerIndex(), objectState.readableBytes());
        long h1 = LongHashFunction.farmUo().hashBytes(b);
        long h2 = LongHashFunction.xx().hashBytes(b);

        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer((2 * Long.SIZE) / Byte.SIZE);
        try {
            buf.writeLong(h1).writeLong(h2);
            byte[] dst = new byte[buf.readableBytes()];
            buf.readBytes(dst);
            return dst;
        } finally {
            buf.release();
        }
    }


    public static String hashToBase64(ByteBuf objectState) {
        ByteBuffer bf = objectState.internalNioBuffer(objectState.readerIndex(), objectState.readableBytes());
        long h1 = LongHashFunction.farmUo().hashBytes(bf);
        long h2 = LongHashFunction.xx().hashBytes(bf);

        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer((2 * Long.SIZE) / Byte.SIZE);
        try {
            buf.writeLong(h1).writeLong(h2);
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
