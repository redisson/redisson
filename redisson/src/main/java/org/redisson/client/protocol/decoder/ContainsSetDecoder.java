/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.redisson.client.handler.State;

/**
 * 
 * @author Su Ko
 *
 */
public class ContainsSetDecoder<T> implements MultiDecoder<Set<T>> {

    private final List<T> args;

    public ContainsSetDecoder(Collection<T> args) {
        if (args instanceof List) {
            this.args = (List<T>) args;
        } else {
            this.args = new ArrayList<>(args);
        }
    }

    @Override
    public Set<T> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return Collections.emptySet();
        }

        Set<T> result = new LinkedHashSet<>(parts.size());
        for (int index = 0; index < parts.size(); index++) {
            Long value = (Long) parts.get(index);
            if (value == 1) {
                result.add(args.get(index));
            }
        }

        return result;
    }

}
