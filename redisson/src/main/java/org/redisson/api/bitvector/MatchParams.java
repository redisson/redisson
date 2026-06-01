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
package org.redisson.api.bitvector;

import java.time.Duration;

public final class MatchParams implements MatchArgs {

    long mask;
    int chunkSize = 10;
    Duration chunkFetchTTL = Duration.ofMinutes(5);

    MatchParams(long mask) {
        this.mask = mask;
    }

    @Override
    public MatchArgs chunkSize(int value) {
        this.chunkSize = value;
        return this;
    }

    @Override
    public MatchArgs chunkFetchTTL(Duration value) {
        this.chunkFetchTTL = value;
        return this;
    }

    public long getMask() {
        return mask;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public Duration getChunkFetchTTL() {
        return chunkFetchTTL;
    }
}
