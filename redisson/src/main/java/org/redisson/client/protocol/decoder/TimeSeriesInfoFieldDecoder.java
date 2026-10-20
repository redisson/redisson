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

import org.redisson.api.tsnative.TSInfo;
import org.redisson.client.handler.State;

import java.util.List;
import java.util.function.Function;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <T> field type
 *
 */
public class TimeSeriesInfoFieldDecoder<T> implements MultiDecoder<T> {

    private final TimeSeriesInfoDecoder info = new TimeSeriesInfoDecoder();
    private final Function<TSInfo, T> field;

    public TimeSeriesInfoFieldDecoder(Function<TSInfo, T> field) {
        this.field = field;
    }

    public static long timestampOf(Long timestamp) {
        if (timestamp == null) {
            return 0;
        }
        return timestamp;
    }

    @Override
    public T decode(List<Object> parts, State state) {
        return field.apply(info.decode(parts, state));
    }

}
