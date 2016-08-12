/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.connection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommand;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class FutureConnectionListener<T extends RedisConnection> implements FutureListener<Object> {

    private final AtomicInteger commandsCounter = new AtomicInteger();

    private final Promise<T> connectionPromise;
    private final T connection;
    private final List<Runnable> commands = new ArrayList<Runnable>(4);

    public FutureConnectionListener(Promise<T> connectionFuture, T connection) {
        super();
        this.connectionPromise = connectionFuture;
        this.connection = connection;
    }

    public void addCommand(final RedisCommand<?> command, final Object ... params) {
        commandsCounter.incrementAndGet();
        commands.add(new Runnable() {
            @Override
            public void run() {
                Future<Object> future = connection.async(command, params);
                future.addListener(FutureConnectionListener.this);
            }
        });
    }

    public void executeCommands() {
        if (commands.isEmpty()) {
            connectionPromise.setSuccess(connection);
            return;
        }

        for (Runnable command : commands) {
            command.run();
        }
        commands.clear();
    }

    @Override
    public void operationComplete(Future<Object> future) throws Exception {
        if (!future.isSuccess()) {
            connection.closeAsync();
            connectionPromise.tryFailure(future.cause());
            return;
        }
        if (commandsCounter.decrementAndGet() == 0) {
            connectionPromise.trySuccess(connection);
        }
    }

}
