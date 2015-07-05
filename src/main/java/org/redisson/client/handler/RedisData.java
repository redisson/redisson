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
package org.redisson.client.handler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.client.protocol.RedisCommand;

import io.netty.util.concurrent.Promise;

public class RedisData<R> {

    Promise<R> promise;
    RedisCommand<R> command;
    Object[] params;
    AtomicBoolean send = new AtomicBoolean();

    public RedisData(Promise<R> promise, RedisCommand<R> command, Object[] params) {
        this.promise = promise;
        this.command = command;
        this.params = params;
    }

    public RedisCommand<R> getCommand() {
        return command;
    }

    public Object[] getParams() {
        return params;
    }

    public Promise<R> getPromise() {
        return promise;
    }

    public AtomicBoolean getSend() {
        return send;
    }

}
