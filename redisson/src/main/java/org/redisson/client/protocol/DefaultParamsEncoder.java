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
package org.redisson.client.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class DefaultParamsEncoder implements Encoder {

    @Override
    public ByteBuf encode(Object in) {
        if (in instanceof byte[]) {
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
            buf.writeBytes((byte[])in);
            return buf;
        }
        if (in instanceof ByteBuf) {
            return (ByteBuf) in;
        }
        
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        buf.writeCharSequence(in.toString(), CharsetUtil.UTF_8);
        return buf;
    }

}
