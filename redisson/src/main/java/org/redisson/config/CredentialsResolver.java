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
package org.redisson.config;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * Credentials resolver object which is invoked during connection/reconnection process.
 * It makes possible to specify dynamically changing Redis credentials.
 *
 * @author Nikita Koksharov
 *
 */
public interface CredentialsResolver {

    /**
     * Asynchronously resolves Credentials object
     * for specified Redis node <code>address</code> .
     *
     * @param address address of Redis node
     * @return Credentials object
     */
    CompletionStage<Credentials> resolve(InetSocketAddress address);

    /**
     * Returns the time-to-live duration for the resolved credentials,
     * which begins when the connection is established.
     *
     * This indicates how long the credentials should be considered valid
     * before they need to be refreshed or renewed.
     * <p>
     * Default implementation returns Duration.ZERO, meaning credentials
     * don't expire and won't be automatically refreshed based on time.
     *
     * @return Duration representing the time-to-live for credentials,
     *         or Duration.ZERO if credentials don't expire
     */
    default Duration timeToLive() {
        return Duration.ZERO;
    }

    /**
     * Registers a callback that will be invoked when authentication
     * credentials need to be renewed.
     * <p>
     * The implementation must invoke a callback
     * only after the object returned by {@link #resolve(InetSocketAddress)}
     * method has been updated.
     *
     * @see EntraIdCredentialsResolver
     *
     * @param callback Runnable callback to be executed when auth renewal is needed
     */
    default void addRenewAuthCallback(Runnable callback) {
    }

    /**
     * Unregisters a previously added authentication renewal callback.
     *
     * @see EntraIdCredentialsResolver
     *
     * @param callback Runnable callback to be removed
     */
    default void removeRenewAuthCallback(Runnable callback) {
    }

}
