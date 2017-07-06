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

import org.redisson.api.RFuture;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RedissonPromise;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <R> result type
 */
public class MapWriterPromise<R> extends RedissonPromise<R> {

    public MapWriterPromise(RFuture<R> f, final CommandAsyncExecutor commandExecutor, final MapWriterTask<R> task) {
        f.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(final Future<R> future) throws Exception {
                if (!future.isSuccess()) {
                    tryFailure(future.cause());
                    return;
                }

                if (task.condition(future)) {
                    commandExecutor.getConnectionManager().getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                task.execute();
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
        });
    }

}
