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

import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Detects failed Redis node if it has reached specified amount of command execution errors
 * <code>failedCommandsLimit</code> in <code>checkInterval</code> time interval.
 *
 * @author Nikita Koksharov
 *
 */
public class FailedCommandsDetector implements FailedNodeDetector {

    protected long checkInterval;

    protected long failedCommandsLimit;

    private final NavigableMap<Long, Long> failedCommands = new TreeMap<>();

    public FailedCommandsDetector() {
    }

    public FailedCommandsDetector(long checkInterval, int failedCommandsLimit) {
        if (checkInterval == 0) {
            throw new IllegalArgumentException("checkInterval value");
        }
        if (failedCommandsLimit == 0) {
            throw new IllegalArgumentException("failedCommandsLimit value");
        }
        this.checkInterval = checkInterval;
        this.failedCommandsLimit = failedCommandsLimit;
    }

    public void setCheckInterval(long checkInterval) {
        if (checkInterval == 0) {
            throw new IllegalArgumentException("checkInterval value");
        }
        this.checkInterval = checkInterval;
    }

    public void setFailedCommandsLimit(long failedCommandsLimit) {
        if (failedCommandsLimit == 0) {
            throw new IllegalArgumentException("failedCommandsLimit value");
        }
        this.failedCommandsLimit = failedCommandsLimit;
    }

    @Override
    public void onConnectFailed() {
    }

    @Override
    public void onConnectFailed(Throwable cause) {
    }

    @Override
    public void onConnectSuccessful() {
    }

    @Override
    public void onPingSuccessful() {
    }

    @Override
    public void onCommandSuccessful() {
    }

    @Override
    public void onPingFailed() {
    }

    @Override
    public void onPingFailed(Throwable cause) {
    }

    @Override
    public synchronized void onCommandFailed(Throwable cause) {
        failedCommands.merge(getCurrentTime(), 1L, Long::sum);
    }

    @Override
    public synchronized boolean isNodeFailed() {
        if (failedCommandsLimit == 0) {
            throw new IllegalArgumentException("failedCommandsLimit isn't set");
        }

        long start = getCurrentTime() - checkInterval;
        failedCommands.headMap(start).clear();

        long failures = failedCommands.values().stream().mapToLong(Long::longValue).sum();
        if (failures >= failedCommandsLimit) {
            failedCommands.clear();
            return true;
        }
        return false;
    }

    protected long getCurrentTime() {
        return System.currentTimeMillis();
    }

    @Override
    public FailedNodeDetector copy() {
        return new FailedCommandsDetector(checkInterval, (int) failedCommandsLimit);
    }

}
