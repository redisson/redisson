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

import org.redisson.api.array.ArrayFullInfo;
import org.redisson.client.handler.State;

import java.util.List;
import java.util.Map;

/**
 * Full array information decoder.
 *
 * @author Nikita Koksharov
 *
 */
public class ArrayFullInfoDecoder extends AbstractArrayInfoDecoder implements MultiDecoder<ArrayFullInfo> {

    @Override
    public ArrayFullInfo decode(List<Object> parts, State state) {
        Map<String, Object> map = toMap(parts);

        ArrayFullInfo info = new ArrayFullInfo();
        populateBase(map, info);
        setLong(map, "dense-slices", info::setDenseSlices);
        setLong(map, "sparse-slices", info::setSparseSlices);
        setDouble(map, "avg-dense-size", info::setAverageDenseSize);
        setDouble(map, "avg-dense-fill", info::setAverageDenseFill);
        setDouble(map, "avg-sparse-size", info::setAverageSparseSize);
        return info;
    }

}
