/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson.client.protocol;

import org.redisson.misc.LogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CommandsData implements QueueCommand {

    private final List<CommandData<?, ?>> commands;
    private final List<CommandData<?, ?>> attachedCommands;
    private final CompletableFuture<Void> promise;
    private final boolean skipResult;
    private final boolean atomic;
    private final boolean queued;
    private final boolean syncSlaves;

    public CommandsData(CompletableFuture<Void> promise, List<CommandData<?, ?>> commands, boolean queued, boolean syncSlaves) {
        this(promise, commands, null, false, false, queued, syncSlaves);
    }
    
    public CommandsData(CompletableFuture<Void> promise, List<CommandData<?, ?>> commands, boolean skipResult, boolean atomic, boolean queued, boolean syncSlaves) {
        this(promise, commands, null, skipResult, atomic, queued, syncSlaves);
    }
    
    public CommandsData(CompletableFuture<Void> promise, List<CommandData<?, ?>> commands, List<CommandData<?, ?>> attachedCommands,
            boolean skipResult, boolean atomic, boolean queued, boolean syncSlaves) {
        super();
        this.promise = promise;
        this.commands = commands;
        this.skipResult = skipResult;
        this.atomic = atomic;
        this.attachedCommands = attachedCommands;
        this.queued = queued;
        this.syncSlaves = syncSlaves;
    }

    public boolean isSyncSlaves() {
        return syncSlaves;
    }

    public CompletableFuture<Void> getPromise() {
        return promise;
    }

    public boolean isQueued() {
        return queued;
    }
    
    public boolean isAtomic() {
        return atomic;
    }
    
    public boolean isSkipResult() {
        return skipResult;
    }
    
    public List<CommandData<?, ?>> getAttachedCommands() {
        return attachedCommands;
    }
    
    public List<CommandData<?, ?>> getCommands() {
        return commands;
    }

    @Override
    public List<CommandData<Object, Object>> getPubSubOperations() {
        List<CommandData<Object, Object>> result = new ArrayList<CommandData<Object, Object>>();
        for (CommandData<?, ?> commandData : commands) {
            if (RedisCommands.PUBSUB_COMMANDS.contains(commandData.getCommand().getName())) {
                result.add((CommandData<Object, Object>) commandData);
            }
        }
        return result;
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return promise.completeExceptionally(cause);
    }

    @Override
    public String toString() {
        return "CommandsData{" +
                "commands=" + LogHelper.toString(commands) +
                ", promise=" + promise +
                ", skipResult=" + skipResult +
                ", atomic=" + atomic +
                ", queued=" + queued +
                ", syncSlaves=" + syncSlaves +
                '}';
    }

    @Override
    public boolean isExecuted() {
        return promise.isDone();
    }

    @Override
    public boolean isBlockingCommand() {
        return false;
    }

}
