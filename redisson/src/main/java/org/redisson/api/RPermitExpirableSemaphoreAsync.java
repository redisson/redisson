/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
 * Asynchronous interface for Semaphore object with lease time parameter support for each acquired permit.
 * 
 * <p>Each permit identified by own id and could be released only using its id.
 * Permit id is a 128-bits unique random identifier generated each time during acquiring.
 *   
 * <p>Works in non-fair mode. Therefore order of acquiring is unpredictable.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RPermitExpirableSemaphoreAsync extends RExpirableAsync {

    /**
     * Acquires a permit and returns its id.
     * Waits if necessary until a permit became available.
     * 
     * @return permit id
     */
    RFuture<String> acquireAsync();
    
    /**
     * Acquires a permit with defined <code>leaseTime</code> and return its id.
     * Waits if necessary until a permit became available.
     * 
     * @param leaseTime permit lease time
     * @param unit time unit
     * @return permit id
     */
    RFuture<String> acquireAsync(long leaseTime, TimeUnit unit);
    
    /**
     * Tries to acquire currently available permit and return its id.
     *
     * @return permit id if a permit was acquired and {@code null}
     *         otherwise
     */
    RFuture<String> tryAcquireAsync();

    /**
     * Tries to acquire currently available permit and return its id.
     * Waits up to defined <code>waitTime</code> if necessary until a permit became available.
     * 
     * @param waitTime the maximum time to wait
     * @param unit the time unit
     * @return permit id if a permit was acquired and {@code null}
     *         if the waiting time elapsed before a permit was acquired
     */
    RFuture<String> tryAcquireAsync(long waitTime, TimeUnit unit);

    /**
     * Tries to acquire currently available permit
     * with defined <code>leaseTime</code> and return its id.
     * Waits up to defined <code>waitTime</code> if necessary until a permit became available.
     * 
     * @param waitTime the maximum time to wait
     * @param leaseTime permit lease time, use -1 to make it permanent
     * @param unit the time unit
     * @return permit id if a permit was acquired and <code>null</code>
     *         if the waiting time elapsed before a permit was acquired
     */
    RFuture<String> tryAcquireAsync(long waitTime, long leaseTime, TimeUnit unit);

    /**
     * Tries to release permit by its id.
     *
     * @param permitId permit id
     * @return <code>true</code> if a permit has been released and <code>false</code>
     *         otherwise
     */
    RFuture<Boolean> tryReleaseAsync(String permitId);

    /**
     * Releases a permit by its id. Increases the number of available permits.
     * Throws an exception if permit id doesn't exist or has already been released.
     * 
     * @param permitId - permit id
     * @return void
     */
    RFuture<Void> releaseAsync(String permitId);

    /**
     * Returns amount of available permits.
     *
     * @return number of permits
     */
    RFuture<Integer> availablePermitsAsync();

    /**
     * Tries to set number of permits.
     *
     * @param permits - number of permits
     * @return <code>true</code> if permits has been set successfully, otherwise <code>false</code>.  
     */
    RFuture<Boolean> trySetPermitsAsync(int permits);

    /**
     * Increases or decreases the number of available permits by defined value. 
     *
     * @param permits amount of permits to add/remove
     * @return void
     */
    RFuture<Void> addPermitsAsync(int permits);
    
    /**
     * Overrides and updates lease time for defined permit id.
     * 
     * @param permitId permit id
     * @param leaseTime permit lease time, use -1 to make it permanent
     * @param unit the time unit
     * @return <code>true</code> if permits has been updated successfully, otherwise <code>false</code>.
     */
    RFuture<Boolean> updateLeaseTimeAsync(String permitId, long leaseTime, TimeUnit unit);
    
}
