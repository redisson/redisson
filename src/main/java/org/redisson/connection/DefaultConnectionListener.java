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

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.NodeType;

import io.netty.util.concurrent.Promise;

public class DefaultConnectionListener implements ConnectionInitializer {

    public final <T extends RedisConnection> void onConnect(Promise<T> connectionFuture, T conn, NodeType nodeType, MasterSlaveServersConfig config) {
        FutureConnectionListener<T> listener = new FutureConnectionListener<T>(connectionFuture, conn);
        doConnect(config, nodeType, listener);
        listener.executeCommands();
    }

    protected void doConnect(MasterSlaveServersConfig config, NodeType nodeType, FutureConnectionListener<? extends RedisConnection> connectionListener)
            throws RedisException {
        if (config.getPassword() != null) {
            connectionListener.addCommand(RedisCommands.AUTH, config.getPassword());
        }
        if (config.getDatabase() != 0) {
            connectionListener.addCommand(RedisCommands.SELECT, config.getDatabase());
        }
        if (config.getClientName() != null) {
            connectionListener.addCommand(RedisCommands.CLIENT_SETNAME, config.getClientName());
        }
    }

}
