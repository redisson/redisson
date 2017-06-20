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
package org.redisson;

import java.util.concurrent.ExecutorService;

import org.redisson.api.RFuture;
import org.redisson.command.CommandAsyncExecutor;

import io.netty.util.concurrent.Future;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <R> result type
 */
public abstract class MapWriterExecutorPromise<R> extends MapWriterPromise<R> {

    public MapWriterExecutorPromise(RFuture<R> f, CommandAsyncExecutor commandExecutor) {
        super(f, commandExecutor);
    }

    @Override
    public void execute(final Future<R> future, ExecutorService executorService) {
        if (condition(future)) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        executeWriter();
                    } catch (Exception e) {
                        tryFailure(e);
                        return;
                    }
                    trySuccess(future.getNow());
                }
            });
        } else {
            trySuccess(future.getNow());
        }
    }

    protected boolean condition(Future<R> future) {
        return true;
    }

    protected abstract void executeWriter();

}
