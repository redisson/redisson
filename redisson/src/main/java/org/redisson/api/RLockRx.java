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

import io.reactivex.Flowable;

/**
 * RxJava2 interface for Lock object
 * 
 * @author Nikita Koksharov
 *
 */
public interface RLockRx extends RExpirableRx {

    Flowable<Boolean> forceUnlock();
    
    Flowable<Void> unlock();
    
    Flowable<Void> unlock(long threadId);
    
    Flowable<Boolean> tryLock();

    Flowable<Void> lock();

    Flowable<Void> lock(long threadId);
    
    Flowable<Void> lock(long leaseTime, TimeUnit unit);
    
    Flowable<Void> lock(long leaseTime, TimeUnit unit, long threadId);
    
    Flowable<Boolean> tryLock(long threadId);
    
    Flowable<Boolean> tryLock(long waitTime, TimeUnit unit);

    Flowable<Boolean> tryLock(long waitTime, long leaseTime, TimeUnit unit);

    Flowable<Boolean> tryLock(long waitTime, long leaseTime, TimeUnit unit, long threadId);

    
}
