/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects failed Redis node if it has ongoing connection errors in <code>checkInterval</code> time interval.
 *
 * @author Nikita Koksharov
 *
 */
public class FailedConnectionDetector implements FailedNodeDetector {

    private long checkInterval;

    private final AtomicLong firstFailTime = new AtomicLong(0);

    public FailedConnectionDetector() {
        this(180000);
    }

    public FailedConnectionDetector(long checkInterval) {
        if (checkInterval == 0) {
            throw new IllegalArgumentException("checkInterval value");
        }

        this.checkInterval = checkInterval;
    }

    public void setCheckInterval(long checkInterval) {
        if (checkInterval == 0) {
            throw new IllegalArgumentException("checkInterval value");
        }

        this.checkInterval = checkInterval;
    }

    @Override
    public void onConnectFailed() {
        firstFailTime.compareAndSet(0, System.currentTimeMillis());
    }

    @Override
    public void onConnectSuccessful() {
        firstFailTime.set(0);
    }

    @Override
    public void onPingSuccessful() {
        firstFailTime.set(0);
    }

    @Override
    public void onCommandSuccessful() {
    }

    @Override
    public void onPingFailed() {
        firstFailTime.compareAndSet(0, System.currentTimeMillis());
    }

    @Override
    public void onCommandFailed(Throwable cause) {
    }

    @Override
    public boolean isNodeFailed() {
        if (firstFailTime.get() != 0 && checkInterval > 0) {
            return System.currentTimeMillis() - firstFailTime.get() > checkInterval;
        }

        return false;
    }

}
