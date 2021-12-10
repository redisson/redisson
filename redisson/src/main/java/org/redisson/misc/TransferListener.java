/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.misc;

import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public class TransferListener<T> implements BiConsumer<Object, Throwable> {

    private final RPromise<T> promise;
    private final T value;

    public TransferListener(RPromise<T> promise) {
        this(promise, null);
    }

    public TransferListener(RPromise<T> promise, T value) {
        super();
        this.promise = promise;
        this.value = value;
    }

    @Override
    public void accept(Object t, Throwable u) {
        if (u != null) {
            if (u instanceof CompletionException) {
                u = u.getCause();
            }
            promise.tryFailure(u);
            return;
        }

        if (value != null) {
            promise.trySuccess(value);
        } else {
            promise.trySuccess((T) t);
        }
    }
    
}
