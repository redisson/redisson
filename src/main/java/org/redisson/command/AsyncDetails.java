/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.command;

import org.redisson.client.RedisException;

import io.netty.channel.ChannelFuture;
import io.netty.util.Timeout;

public class AsyncDetails {

    private volatile ChannelFuture writeFuture;

    private volatile RedisException exception;

    private volatile Timeout timeout;

    public ChannelFuture getWriteFuture() {
        return writeFuture;
    }
    public void setWriteFuture(ChannelFuture writeFuture) {
        this.writeFuture = writeFuture;
    }

    public RedisException getException() {
        return exception;
    }
    public void setException(RedisException exception) {
        this.exception = exception;
    }

    public Timeout getTimeout() {
        return timeout;
    }
    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

}
