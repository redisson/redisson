/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.misc.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class ThreadLocalSemaphore {

    private final ThreadLocal<Semaphore> semaphore;
    private final Set<Semaphore> allValues = Collections.newSetFromMap(new ConcurrentHashMap<Semaphore, Boolean>());

    public ThreadLocalSemaphore() {
        semaphore = new ThreadLocal<Semaphore>() {
            @Override protected Semaphore initialValue() {
                Semaphore value = new Semaphore(1);
                value.acquireUninterruptibly();
                allValues.add(value);
                return value;
            }
        };
    }

    public Semaphore get() {
        return semaphore.get();
    }

    public void remove() {
        allValues.remove(get());
        semaphore.remove();
    }

    public Collection<Semaphore> getAll() {
        return allValues;
    }

}
