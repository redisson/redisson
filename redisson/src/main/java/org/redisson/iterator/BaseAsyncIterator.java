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
package org.redisson.iterator;

import org.redisson.ScanResult;
import org.redisson.api.AsyncIterator;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 *
 * @author seakider
 *
 */
public abstract class BaseAsyncIterator<V, E> implements AsyncIterator<V> {
    private Iterator<E> lastIt;
    protected String nextItPos = "0";
    protected RedisClient client;
    
    @Override
    public CompletionStage<Boolean> hasNext() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (nextItPos == null && (lastIt == null || !lastIt.hasNext())) {
            result.complete(false);
            return result;
        }
        if (lastIt == null || !lastIt.hasNext()) {
            iterator(client, nextItPos).whenComplete((v, e) -> {
                if (e != null || v == null) {
                    client = null;
                    nextItPos = null;
                    result.complete(false);
                } else {
                    client = v.getRedisClient();
                    nextItPos = v.getPos();
                    lastIt = v.getValues().iterator();
                    if ("0".equals(nextItPos)) {
                        nextItPos = null;
                    }
                    result.complete(lastIt.hasNext());
                }
            });
        } else {
            result.complete(true);
        }
        return result;
    }
    
    @Override
    public CompletionStage<V> next() {
        CompletableFuture<V> result = new CompletableFuture<>();
        hasNext().thenAccept(v -> {
            if (!v) {
                result.completeExceptionally(new NoSuchElementException());
                return;
            }
            E next = lastIt.next();
            result.complete(getValue(next));
        });
        return result;
    }
    
    protected abstract RFuture<ScanResult<E>> iterator(RedisClient client, String nextItPos);
    
    protected V getValue(E entry) {
        return (V) entry;
    }
}
