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
package org.redisson.remote;

import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.reactive.CommandReactiveExecutor;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ReactiveRemoteProxy extends AsyncRemoteProxy {

    public ReactiveRemoteProxy(CommandAsyncExecutor commandExecutor, String name, String responseQueueName,
                                Codec codec, String executorId, String cancelRequestMapName, BaseRemoteService remoteService) {
        super(convert(commandExecutor), name, responseQueueName, codec, executorId, cancelRequestMapName, remoteService);
    }

    private static CommandAsyncExecutor convert(CommandAsyncExecutor commandExecutor) {
        if (commandExecutor instanceof CommandReactiveExecutor) {
            return commandExecutor;
        }
        return CommandReactiveExecutor.create(commandExecutor.getConnectionManager(), commandExecutor.getObjectBuilder());
    }

    @Override
    protected List<Class<?>> permittedClasses() {
        return Arrays.asList(Mono.class);
    }

    @Override
    protected Object convertResult(RemotePromise<Object> result, Class<?> returnType) {
        return ((CommandReactiveExecutor) commandExecutor).reactive(() -> new CompletableFutureWrapper<>(result));
    }
    
}
