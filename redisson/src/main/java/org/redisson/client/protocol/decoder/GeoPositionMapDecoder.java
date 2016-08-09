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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;

public class GeoPositionMapDecoder implements MultiDecoder<Map<Object, Object>> {

    private final List<Object> args;

    public GeoPositionMapDecoder(List<Object> args) {
        this.args = args;
    }

    @Override
    public Double decode(ByteBuf buf, State state) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isApplicable(int paramNum, State state) {
        return false;
    }

    @Override
    public Map<Object, Object> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Object, Object> result = new HashMap<Object, Object>(parts.size());
        for (int index = 0; index < args.size()-1; index++) {
            Object value = parts.get(index);
            if (value == null || value == Collections.emptyMap()) {
                continue;
            }
            result.put(args.get(index+1), value);
        }
        return result;
    }

}
