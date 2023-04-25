/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.rx.CommandRxExecutor;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RxRemoteProxy extends AsyncRemoteProxy {

    public RxRemoteProxy(CommandAsyncExecutor commandExecutor, String name, String responseQueueName,
            ConcurrentMap<String, ResponseEntry> responses, Codec codec, String executorId,
            String cancelRequestMapName, BaseRemoteService remoteService) {
        super(commandExecutor, name, responseQueueName, responses, codec, executorId, cancelRequestMapName,
                remoteService);
    }

    @Override
    protected List<Class<?>> permittedClasses() {
        return Arrays.asList(Completable.class, Single.class, Maybe.class);
    }
    
    @Override
    protected Object convertResult(RemotePromise<Object> result, Class<?> returnType) {
        Flowable<Object> flowable = ((CommandRxExecutor) commandExecutor).flowable(() -> new CompletableFutureWrapper<>(result));
        
        if (returnType == Completable.class) {
            return flowable.ignoreElements();
        }
        if (returnType == Single.class) {
            return flowable.singleOrError();
        }
        return flowable.singleElement();
    }
    
}
