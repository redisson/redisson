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
package org.redisson;

import org.redisson.misc.RPromise;
import org.redisson.misc.ReclosableLatch;

public class RedissonCountDownLatchEntry implements PubSubEntry<RedissonCountDownLatchEntry> {

    private int counter;

    private final ReclosableLatch latch;
    private final RPromise<RedissonCountDownLatchEntry> promise;

    public RedissonCountDownLatchEntry(RPromise<RedissonCountDownLatchEntry> promise) {
        super();
        this.latch = new ReclosableLatch();
        this.promise = promise;
    }

    public void aquire() {
        counter++;
    }

    public int release() {
        return --counter;
    }

    public RPromise<RedissonCountDownLatchEntry> getPromise() {
        return promise;
    }

    public ReclosableLatch getLatch() {
        return latch;
    }

}
