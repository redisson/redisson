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

import org.redisson.api.array.ArrayInfo;
import org.redisson.client.handler.State;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Array information decoder.
 *
 * @author lamnt2008
 *
 */
public class ArrayInfoDecoder implements MultiDecoder<ArrayInfo> {

    @Override
    public ArrayInfo decode(List<Object> parts, State state) {
        Map<String, Object> map = IntStream.range(0, parts.size())
                .filter(i -> i % 2 == 0)
                .filter(i -> i + 1 < parts.size())
                .mapToObj(i -> parts.subList(i, i + 2))
                .filter(p -> p.get(1) != null)
                .collect(Collectors.toMap(e -> (String) e.get(0), e -> e.get(1)));

        ArrayInfo info = new ArrayInfo();
        setLong(map, "count", info::setCount);
        setLong(map, "len", info::setLength);
        setLong(map, "next-insert-index", info::setNextInsertIndex);
        setLong(map, "slices", info::setSlices);
        setLong(map, "directory-size", info::setDirectorySize);
        setLong(map, "super-dir-entries", info::setSuperDirectoryEntries);
        setLong(map, "slice-size", info::setSliceSize);
        setLong(map, "dense-slices", info::setDenseSlices);
        setLong(map, "sparse-slices", info::setSparseSlices);
        setDouble(map, "avg-dense-size", info::setAverageDenseSize);
        setDouble(map, "avg-dense-fill", info::setAverageDenseFill);
        setDouble(map, "avg-sparse-size", info::setAverageSparseSize);
        return info;
    }

    private void setLong(Map<String, Object> map, String key, Consumer<Long> setter) {
        Object value = map.get(key);
        if (value != null) {
            setter.accept(toLong(value));
        }
    }

    private void setDouble(Map<String, Object> map, String key, Consumer<Double> setter) {
        Object value = map.get(key);
        if (value != null) {
            setter.accept(toDouble(value));
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

}
