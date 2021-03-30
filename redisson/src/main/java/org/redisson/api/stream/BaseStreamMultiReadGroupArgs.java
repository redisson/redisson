/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.api.StreamMessageId;

import java.time.Duration;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
class BaseStreamMultiReadGroupArgs implements StreamMultiReadGroupArgs, StreamReadGroupSource {

    private final StreamReadGroupParams params;

    BaseStreamMultiReadGroupArgs(StreamMessageId id1, Map<String, StreamMessageId> offsets) {
        this.params = new StreamReadGroupParams(id1, offsets);
    }

    @Override
    public StreamMultiReadGroupArgs noAck() {
        params.setNoAck(true);
        return this;
    }

    @Override
    public StreamMultiReadGroupArgs count(int count) {
        params.setCount(count);
        return this;
    }

    @Override
    public StreamMultiReadGroupArgs timeout(Duration timeout) {
        params.setTimeout(timeout);
        return this;
    }

    @Override
    public StreamReadGroupParams getParams() {
        return params;
    }
}
