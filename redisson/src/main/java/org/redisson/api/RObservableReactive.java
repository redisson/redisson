/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.api;

import reactor.core.publisher.Mono;

/**
 * Base Reactive interface for Lock object listener
 *
 * @author seakider
 *
 */
public interface RObservableReactive {
    /**
     * Adds object event listener
     *
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    Mono<Integer> addListener(ObjectListener listener);

    /**
     * Removes object event listener
     *
     * @param listenerId - listener id
     * @return void
     */
    Mono<Void> removeListener(int listenerId);
}
