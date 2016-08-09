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
package org.redisson.client.protocol.decoder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class ListResultReplayDecoder implements MultiDecoder<List<Map<Object, Object>>> {

    @Override
    public Object decode(ByteBuf buf, State state) {
        return buf.toString(CharsetUtil.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<Object, Object>> decode(List<Object> parts, State state) {
        Map<Object, Object>[] res = parts.toArray(new Map[parts.size()]);
        return Arrays.asList(res);
    }

    @Override
    public boolean isApplicable(int paramNum, State state) {
        return true;
    }

}
