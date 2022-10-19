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

import org.redisson.api.PendingEntry;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.convertor.StreamIdConvertor;

import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class PendingEntryDecoder implements MultiDecoder<Object> {

    private final StreamIdConvertor convertor = new StreamIdConvertor();

    @Override
    public Object decode(List<Object> parts, State state) {
        if (parts.isEmpty() || parts.get(0) instanceof PendingEntry) {
            return parts;
        }
        return new PendingEntry(convertor.convert(parts.get(0)), parts.get(1).toString(), 
                Long.valueOf(parts.get(2).toString()), Long.valueOf(parts.get(3).toString()));
    }

}
