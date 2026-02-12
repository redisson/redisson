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
package org.redisson.config;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Credentials resolver interface for dynamically managing Valkey or Redis
 * authentication credentials during connection and reconnection processes.
 *
 * @author Nikita Koksharov
 *
 */
public interface CredentialsResolver {

    /**
     * Asynchronously resolves Credentials object
     * for specified Valkey or Redis node <code>address</code> .
     *
     * @param address address of Valkey or Redis node
     * @return Credentials object
     */
    CompletionStage<Credentials> resolve(InetSocketAddress address);

    /**
     * Returns a CompletionStage that completes when the next credential renewal
     * is needed.
     * <p>
     * The returned CompletionStage should complete when an external authentication
     * system changed credentials and CompletionStage instance returned
     * by {@link #resolve(InetSocketAddress)} method has been updated.
     * <p>
     * For continuous monitoring, implementations should return a new CompletionStage
     * instance after each credentials update to support chaining multiple renewal events.
     *
     * @return CompletionStage that completes when credential renewal is needed.
     *
     * @see EntraIdCredentialsResolver
     *
     */
    default CompletionStage<Void> nextRenewal() {
        return new CompletableFuture<>();
    }

}
