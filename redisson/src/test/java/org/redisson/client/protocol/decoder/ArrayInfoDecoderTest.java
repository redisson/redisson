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

import org.junit.jupiter.api.Test;
import org.redisson.api.array.ArrayInfo;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayInfoDecoderTest {

    @Test
    public void testDecodeSkipsMissingFields() {
        ArrayInfo info = new ArrayInfoDecoder().decode(Arrays.asList("count", 2L, "len", 5L), null);

        assertThat(info.getCount()).isEqualTo(2);
        assertThat(info.getLength()).isEqualTo(5);
        assertThat(info.getNextInsertIndex()).isZero();
        assertThat(info.getDenseSlices()).isNull();
    }

    @Test
    public void testDecodeSkipsNullAndOddTailFields() {
        ArrayInfo info = new ArrayInfoDecoder().decode(Arrays.asList("count", 2L, "slice-size", null, "dangling"), null);

        assertThat(info.getCount()).isEqualTo(2);
        assertThat(info.getSliceSize()).isZero();
    }

}
