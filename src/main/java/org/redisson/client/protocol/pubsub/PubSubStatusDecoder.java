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
package org.redisson.client.protocol.pubsub;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class PubSubStatusDecoder implements MultiDecoder<PubSubStatusMessage> {

    @Override
    public Object decode(ByteBuf buf) {
        String status = buf.toString(CharsetUtil.UTF_8);
        buf.skipBytes(2);
        return status;
    }

    @Override
    public PubSubStatusMessage decode(List<Object> parts) {
        List<String> channels = new ArrayList<String>();
        for (Object part : parts.subList(1, parts.size()-1)) {
            channels.add(part.toString());
        }
        return new PubSubStatusMessage(PubSubStatusMessage.Type.valueOf(parts.get(0).toString().toUpperCase()), channels);
    }

    @Override
    public boolean isApplicable(int paramNum) {
        return true;
    }

}
