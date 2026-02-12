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

/**
 * Arguments for RStream.range() method
 *
 * @author seakider
 */
public interface StreamRangeArgs {

    /**
     * Defines stream range size limit.
     *
     * @param count stream range size limit
     * @return arguments object
     */
    StreamRangeArgs count(int count);

    /**
     * Defines startId in range inclusive
     *
     * @param startId
     * @return next options
     */
    static StreamEndIdArgs<StreamRangeArgs> startId(StreamMessageId startId) {
        return new StreamRangeParams(startId, false);
    }

    /**
     * Defines startId in range exclusive
     *
     * @param startId
     * @return next options
     */
    static StreamEndIdArgs<StreamRangeArgs> startIdExclusive(StreamMessageId startId) {
        return new StreamRangeParams(startId, true);
    }
}
