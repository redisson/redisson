/**
 * Copyright 2016 Nikita Koksharov
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

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 *
 * @author Nikita Koksharov
 *
 */

public interface RLockAsync extends RExpirableAsync {

    RFuture<Boolean> forceUnlockAsync();
    
    RFuture<Void> unlockAsync();
    
    RFuture<Void> unlockAsync(long threadId);
    
    RFuture<Boolean> tryLockAsync();

    RFuture<Void> lockAsync();

    RFuture<Void> lockAsync(long threadId);
    
    RFuture<Void> lockAsync(long leaseTime, TimeUnit unit);
    
    RFuture<Void> lockAsync(long leaseTime, TimeUnit unit, long threadId);
    
    RFuture<Boolean> tryLockAsync(long threadId);
    
    RFuture<Boolean> tryLockAsync(long waitTime, TimeUnit unit);

    RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit);

    RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId);
    
}
