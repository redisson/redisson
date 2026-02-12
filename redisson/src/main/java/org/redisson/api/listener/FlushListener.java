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
package org.redisson.api.listener;

import org.redisson.api.ObjectListener;

import java.net.InetSocketAddress;

/**
 * Redisson Object Event listener for <b>flush</b> event published by Redis.
 * <p>
 * Requires Redis 6.0+
 *
 * @author Nikita Koksharov
 *
 */
@FunctionalInterface
public interface FlushListener extends ObjectListener {

    /**
     * Invoked when `flushdb` or `flushall` command
     * was executed on Redis node.
     *
     * @param address
     */
    void onFlush(InetSocketAddress address);

}
