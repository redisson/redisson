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

import java.net.URI;
import java.util.Set;

import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.config.MasterSlaveServersConfig;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SingleEntry extends MasterSlaveEntry {

    public SingleEntry(Set<ClusterSlotRange> slotRanges, ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        super(slotRanges, connectionManager, config);
    }

    @Override
    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, URI addr) {
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
