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
package org.redisson.rx;

import org.redisson.api.RBlockingQueueAsync;
import org.redisson.api.RListAsync;

import io.reactivex.Flowable;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> - value type
 */
public class RedissonBlockingQueueRx<V> extends RedissonListRx<V> {

    private final RBlockingQueueAsync<V> queue;
    
    public RedissonBlockingQueueRx(RBlockingQueueAsync<V> queue) {
        super((RListAsync<V>) queue);
        this.queue = queue;
    }

    public Flowable<V> takeElements() {
        return ElementsStream.takeElements(() -> {
            return queue.takeAsync();
        });
    }
    
}
