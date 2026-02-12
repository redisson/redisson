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

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class StreamReadParams implements StreamReadArgs {

    private final StreamMessageId id1;
    private int count;
    private Duration timeout;

    StreamReadParams(StreamMessageId id1) {
        this.id1 = id1;
    }

    @Override
    public StreamReadArgs count(int count) {
        this.count = count;
        return this;
    }

    @Override
    public StreamReadArgs timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public StreamMessageId getId1() {
        return id1;
    }

    public int getCount() {
        return count;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
