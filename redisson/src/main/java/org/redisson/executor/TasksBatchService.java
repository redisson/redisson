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
package org.redisson.executor;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.command.CommandExecutor;
import org.redisson.remote.ResponseEntry;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class TasksBatchService extends TasksService {

    private CommandBatchService batchCommandService;
    
    public TasksBatchService(Codec codec, RedissonClient redisson, String name, CommandExecutor commandExecutor, String executorId, ConcurrentMap<String, ResponseEntry> responses) {
        super(codec, redisson, name, commandExecutor, executorId, responses);
        batchCommandService = new CommandBatchService(commandExecutor.getConnectionManager());
    }
    
    @Override
    protected CommandAsyncExecutor getAddCommandExecutor() {
        return batchCommandService;
    }

    public List<Boolean> executeAdd() {
        return (List<Boolean>) batchCommandService.execute();
    }
    
    public RFuture<List<Boolean>> executeAddAsync() {
        return (RFuture<List<Boolean>>)(Object)batchCommandService.executeAsync();
    }

    
}
