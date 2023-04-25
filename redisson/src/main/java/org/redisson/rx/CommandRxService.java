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
package org.redisson.rx;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.LongConsumer;
import io.reactivex.rxjava3.processors.ReplayProcessor;
import org.redisson.api.RFuture;
import org.redisson.command.CommandAsyncService;
import org.redisson.connection.ConnectionManager;
import org.redisson.liveobject.core.RedissonObjectBuilder;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandRxService extends CommandAsyncService implements CommandRxExecutor {

    public CommandRxService(ConnectionManager connectionManager, RedissonObjectBuilder objectBuilder) {
        super(connectionManager, objectBuilder, RedissonObjectBuilder.ReferenceType.RXJAVA);
    }

    @Override
    public <R> Flowable<R> flowable(Callable<RFuture<R>> supplier) {
        ReplayProcessor<R> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {
            @Override
            public void accept(long t) throws Exception {
                RFuture<R> future;
                try {
                    future = supplier.call();
                } catch (Exception e) {
                    p.onError(e);
                    return;
                }
                p.doOnCancel(new Action() {
                    @Override
                    public void run() throws Exception {
                        future.cancel(true);
                    }
                });
                
                future.whenComplete((res, e) -> {
                   if (e != null) {
                       if (e instanceof CompletionException) {
                           e = e.getCause();
                       }
                       p.onError(e);
                       return;
                   }
                   
                   if (res != null) {
                       p.onNext(res);
                   }
                   p.onComplete();
                });
            }
        });
    }

}
