/**
 * Copyright 2018 Nikita Koksharov
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

import org.reactivestreams.Publisher;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RLockReactive extends RExpirableReactive {

    Publisher<Boolean> forceUnlock();
    
    Publisher<Void> unlock();
    
    Publisher<Void> unlock(long threadId);
    
    Publisher<Boolean> tryLock();

    Publisher<Void> lock();

    Publisher<Void> lock(long threadId);
    
    Publisher<Void> lock(long leaseTime, TimeUnit unit);
    
    Publisher<Void> lock(long leaseTime, TimeUnit unit, long threadId);
    
    Publisher<Boolean> tryLock(long threadId);
    
    Publisher<Boolean> tryLock(long waitTime, TimeUnit unit);

    Publisher<Boolean> tryLock(long waitTime, long leaseTime, TimeUnit unit);

    Publisher<Boolean> tryLock(long waitTime, long leaseTime, TimeUnit unit, long threadId);

    
}
