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

import java.net.InetSocketAddress;

/**
 * Detects failed Redis node depending
 * on {@link #isNodeFailed()} method implementation.
 *
 * @author Nikita Koksharov
 *
 */
public interface FailedNodeDetector {

    void onConnectSuccessful();

    default void onConnectSuccessful(InetSocketAddress address) {
        onConnectSuccessful();
    }

    @Deprecated
    void onConnectFailed();

    default void onConnectFailed(Throwable cause) {
        onConnectFailed();
    }

    default void onConnectFailed(Throwable cause, InetSocketAddress address) {
        onConnectFailed(cause);
    }

    void onPingSuccessful();

    default void onPingSuccessful(InetSocketAddress address) {
        onPingSuccessful();
    }

    @Deprecated
    void onPingFailed();

    default void onPingFailed(Throwable cause) {
        onPingFailed();
    }

    default void onPingFailed(Throwable cause, InetSocketAddress address) {
        onPingFailed(cause);
    }

    void onCommandSuccessful();

    default void onCommandSuccessful(InetSocketAddress address) {
        onCommandSuccessful();
    }

    void onCommandFailed(Throwable cause);

    default void onCommandFailed(Throwable cause, InetSocketAddress address) {
        onCommandFailed(cause);
    }

    boolean isNodeFailed();

    default boolean isNodeFailed(InetSocketAddress address) {
        return isNodeFailed();
    }

    /**
     * Returns a detector with the same configuration and independent runtime state.
     *
     * @return detector copy
     */
    default FailedNodeDetector copy() {
        return this;
    }

}
