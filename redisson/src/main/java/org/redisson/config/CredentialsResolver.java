/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

}
