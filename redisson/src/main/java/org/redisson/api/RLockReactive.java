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
package org.redisson.api;

import java.util.concurrent.TimeUnit;

import reactor.core.publisher.Mono;

/**
 * Reactive interface for Lock object
 * 
 * @author Nikita Koksharov
 *
 */
public interface RLockReactive extends RExpirableReactive {

    Mono<Boolean> forceUnlock();
    
    Mono<Void> unlock();
    
    Mono<Void> unlock(long threadId);
    
    Mono<Boolean> tryLock();

    Mono<Void> lock();

    Mono<Void> lock(long threadId);
    
    Mono<Void> lock(long leaseTime, TimeUnit unit);
    
    Mono<Void> lock(long leaseTime, TimeUnit unit, long threadId);
    
    Mono<Boolean> tryLock(long threadId);
    
    Mono<Boolean> tryLock(long waitTime, TimeUnit unit);

    Mono<Boolean> tryLock(long waitTime, long leaseTime, TimeUnit unit);

    Mono<Boolean> tryLock(long waitTime, long leaseTime, TimeUnit unit, long threadId);

    
}
