/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.client.handler.State;

import java.util.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ContainsDecoder<T> implements MultiDecoder<List<T>> {

    private final int shiftIndex = 0;
    private final List<T> args;

    public ContainsDecoder(Collection<T> args) {
        if (args instanceof List) {
            this.args = (List<T>) args;
        } else {
            this.args = new ArrayList<>(args);
        }
    }

    @Override
    public List<T> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> result = new ArrayList<>(parts.size());
        for (int index = 0; index < parts.size()-shiftIndex; index++) {
            Long value = (Long) parts.get(index);
            if (value == 1) {
                result.add(args.get(index + shiftIndex));
            }
        }
        return result;
    }

}
