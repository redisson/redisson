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
package org.redisson.client.protocol;

import java.util.concurrent.atomic.AtomicReference;

import org.redisson.client.RedisRedirectException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> input type
 * @param <R> output type
 */
public class BatchCommandData<T, R> extends CommandData<T, R> implements Comparable<BatchCommandData<T, R>> {

    private final int index;
    private final AtomicReference<RedisRedirectException> redirectError = new AtomicReference<RedisRedirectException>();
    
    public BatchCommandData(RedisCommand<T> command, Object[] params, int index) {
        this(new RedissonPromise<R>(), StringCodec.INSTANCE, command, params, index);
    }
    
    public BatchCommandData(RPromise<R> promise, Codec codec, RedisCommand<T> command, Object[] params, int index) {
        super(promise, codec, command, params);
        this.index = index;
    }
    
    @Override
    public boolean tryFailure(Throwable cause) {
        if (redirectError.get() != null) {
            return false;
        }
        if (cause instanceof RedisRedirectException) {
            return redirectError.compareAndSet(null, (RedisRedirectException) cause);
        }

        return super.tryFailure(cause);
    }
    
    @Override
    public boolean isSuccess() {
        return redirectError.get() == null && super.isSuccess();
    }
    
    @Override
    public Throwable cause() {
        if (redirectError.get() != null) {
            return redirectError.get();
        }
        return super.cause();
    }
    
    public void clearError() {
        redirectError.set(null);
    }

    @Override
    public int compareTo(BatchCommandData<T, R> o) {
        return index - o.index;
    }

}
