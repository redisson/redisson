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
package org.redisson.api.stream;

import java.time.Duration;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class StreamMultiReadParams implements StreamMultiReadArgs {

    private final StreamReadParams params;

    private final Map<String, StreamMessageId> offsets;

    StreamMultiReadParams(StreamMessageId id1, Map<String, StreamMessageId> offsets) {
        this.params = new StreamReadParams(id1);
        this.offsets = offsets;
    }

    @Override
    public StreamMultiReadArgs count(int count) {
        params.count(count);
        return this;
    }

    @Override
    public StreamMultiReadArgs timeout(Duration timeout) {
        params.timeout(timeout);
        return this;
    }

    public StreamMessageId getId1() {
        return params.getId1();
    }

    public int getCount() {
        return params.getCount();
    }

    public Duration getTimeout() {
        return params.getTimeout();
    }

    public Map<String, StreamMessageId> getOffsets() {
        return offsets;
    }
}
