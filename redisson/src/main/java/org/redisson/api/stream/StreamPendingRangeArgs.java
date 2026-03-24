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
 * Arguments object for RStream.listPending() methods.
 *
 * @author seakider
 *
 */
public interface StreamPendingRangeArgs {
    /**
     * Defines groupName of pending messages
     *
     * @param groupName name of group
     * @return next options
     */
    static StreamStartIdArgs<StreamCountArgs> groupName(String groupName) {
        return new StreamPendingRangeParams(groupName);
    }

    /**
     * Defines consumerName of pending messages
     *
     * @param consumerName name of consumer
     * @return arguments object
     */
    StreamPendingRangeArgs consumerName(String consumerName);

    /**
     * Defines minimum idle time limit.
     *
     * @param idleTime minimum idle time of messages
     * @return arguments object
     */
    StreamPendingRangeArgs idleTime(Duration idleTime);
}
