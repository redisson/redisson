/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.BatchOptions;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.BatchCommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.command.CommandBatchService.Entry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.RPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 * @param <R> type of returned value
 */
public class BaseRedisBatchExecutor<V, R> extends RedisExecutor<V, R> {

    final ConcurrentMap<MasterSlaveEntry, Entry> commands;
    final BatchOptions options;
    final AtomicInteger index;
    
    final AtomicBoolean executed;
    
    @SuppressWarnings("ParameterNumber")
    public BaseRedisBatchExecutor(boolean readOnlyMode, NodeSource source, Codec codec, RedisCommand<V> command,
                                  Object[] params, RPromise<R> mainPromise, boolean ignoreRedirect,
                                  ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder,
                                  ConcurrentMap<MasterSlaveEntry, Entry> commands,
                                  BatchOptions options, AtomicInteger index, AtomicBoolean executed, RedissonObjectBuilder.ReferenceType referenceType) {
        
        super(readOnlyMode, source, codec, command, params, mainPromise, ignoreRedirect, connectionManager,
                objectBuilder, referenceType);
        this.commands = commands;
        this.options = options;
        this.index = index;
        this.executed = executed;

        if (options.getRetryAttempts() > 0) {
            this.attempts = options.getRetryAttempts();
        }
        if (options.getRetryInterval() > 0) {
            this.retryInterval  = options.getRetryInterval();
        }
        if (options.getResponseTimeout() > 0) {
            this.responseTimeout = options.getResponseTimeout();
        }
        if (options.getSyncSlaves() > 0) {
            this.responseTimeout += options.getSyncTimeout();
        }
    }

    protected final MasterSlaveEntry getEntry(NodeSource source) {
        if (source.getSlot() != null) {
            MasterSlaveEntry entry = connectionManager.getEntry(source.getSlot());
            if (entry == null) {
                throw connectionManager.createNodeNotFoundException(source);
            }
            return entry;
        }
        return source.getEntry();
    }
    
    protected final void addBatchCommandData(Object[] batchParams) {
        MasterSlaveEntry msEntry = getEntry(source);
        Entry entry = commands.get(msEntry);
        if (entry == null) {
            entry = new Entry();
            Entry oldEntry = commands.putIfAbsent(msEntry, entry);
            if (oldEntry != null) {
                entry = oldEntry;
            }
        }

        if (!readOnlyMode) {
            entry.setReadOnlyMode(false);
        }

        Codec codecToUse = getCodec(codec);
        BatchCommandData<V, R> commandData = new BatchCommandData<V, R>(mainPromise, codecToUse, command, batchParams, index.incrementAndGet());
        entry.getCommands().add(commandData);
    }
        
}
