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
package org.redisson.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;

/**
 * This interface allows to create hooks applied
 * after Netty Bootstrap and Channel object initialization.
 *
 * @author Nikita Koksharov
 *
 */
public interface NettyHook {

    /**
     * Invoked when Redis client created and initialized Netty Bootstrap object.
     *
     * @param bootstrap - Netty Bootstrap object
     */
    void afterBoostrapInitialization(Bootstrap bootstrap);

    /**
     * Invoked when Netty Channel object was created and initialized.
     *
     * @param channel - Netty Channel object
     */
    void afterChannelInitialization(Channel channel);

}
