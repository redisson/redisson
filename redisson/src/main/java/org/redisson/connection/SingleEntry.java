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
package org.redisson.connection;

import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.misc.RedisURI;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SingleEntry extends MasterSlaveEntry {

    public SingleEntry(ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        super(connectionManager, config);
    }

    @Override
    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, RedisURI addr) {
        return super.connectionWriteOp(command);
    }

    @Override
    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command) {
        return super.connectionWriteOp(command);
    }

    @Override
    public void releaseRead(RedisConnection connection) {
        super.releaseWrite(connection);
    }

}
