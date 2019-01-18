/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.client.handler;

import org.redisson.client.RedisClient;
import org.redisson.client.RedisPubSubConnection;

import io.netty.channel.ChannelHandlerContext;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisPubSubConnectionHandler extends BaseConnectionHandler<RedisPubSubConnection> {

    public RedisPubSubConnectionHandler(RedisClient redisClient) {
        super(redisClient);
    }
    
    @Override
    RedisPubSubConnection createConnection(ChannelHandlerContext ctx) {
        return new RedisPubSubConnection(redisClient, ctx.channel(), connectionPromise);
    }

}
