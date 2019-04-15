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

import java.util.concurrent.ConcurrentMap;

import org.redisson.RedissonRemoteService;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncService;
import org.redisson.remote.RemoteServiceRequest;
import org.redisson.remote.ResponseEntry;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorRemoteService extends RedissonRemoteService {

    public RedissonExecutorRemoteService(Codec codec, String name,
            CommandAsyncService commandExecutor, String executorId, ConcurrentMap<String, ResponseEntry> responses) {
        super(codec, name, commandExecutor, executorId, responses);
    }

    @Override
    protected RFuture<RemoteServiceRequest> getTask(String requestId, RMap<String, RemoteServiceRequest> tasks) {
        return tasks.getAsync(requestId);
    }
    
}
