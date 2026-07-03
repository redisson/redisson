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
 * Detects failed Redis node depending
 * on {@link #isNodeFailed()} method implementation.
 *
 * @author Nikita Koksharov
 *
 */
public interface FailedNodeDetector {

    void onConnectSuccessful();

    @Deprecated
    void onConnectFailed();

    default void onConnectFailed(Throwable cause) {
        onConnectFailed();
    }

    void onPingSuccessful();

    @Deprecated
    void onPingFailed();

    default void onPingFailed(Throwable cause) {
        onPingFailed();
    }

    void onCommandSuccessful();

    void onCommandFailed(Throwable cause);

    boolean isNodeFailed();

    /**
     * Returns a copy of this detector that keeps the same configuration
     * but starts with fresh runtime state. It is used to assign an independent
     * detector to each Redis node, so that failures of one node don't pollute
     * the failure state of another.
     * <p>
     * The default implementation returns the same instance, preserving the
     * shared state. Custom detectors holding per-node runtime state should
     * override this method.
     *
     * @return detector copy with fresh runtime state
     */
    default FailedNodeDetector copy() {
        return this;
    }

}
