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
package org.redisson.client;

/**
 * Detects failed Redis node if it has certain amount of command execution timeout errors
 * <code>failedCommandsLimit</code> in <code>checkInterval</code> time interval.
 *
 * @author Nikita Koksharov
 *
 */
public class FailedCommandsTimeoutDetector extends FailedCommandsDetector {

    public FailedCommandsTimeoutDetector() {
    }

    public FailedCommandsTimeoutDetector(long checkInterval, int failedCommandsLimit) {
        super(checkInterval, failedCommandsLimit);
    }

    @Override
    public void onCommandFailed(Throwable cause) {
        if (cause instanceof RedisTimeoutException) {
            super.onCommandFailed(cause);
        }
    }

}
