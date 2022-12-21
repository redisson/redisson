/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.client.protocol.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class PermitDecoder implements Decoder<String> {

    @Override
    public String decode(ByteBuf buf, State state) {
        if (!buf.isReadable()) {
            return null;
        }
        if (buf.isReadable(14)
                && !buf.isReadable(16)
                && buf.getByte(buf.readerIndex()) == (byte) ':') {
            return buf.toString(CharsetUtil.UTF_8);
        }
        return ByteBufUtil.hexDump(buf);
    }

}
