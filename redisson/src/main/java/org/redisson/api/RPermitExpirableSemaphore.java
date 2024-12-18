/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Semaphore object with lease time parameter support for each acquired permit.
 * 
 * <p>Each permit identified by own id and could be released only using its id.
 * Permit id is a 128-bits unique random identifier generated each time during acquiring.
 *   
 * <p>Works in non-fair mode. Therefore order of acquiring is unpredictable.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RPermitExpirableSemaphore extends RExpirable, RPermitExpirableSemaphoreAsync {

    /**
     * Acquires a permit and returns its id.
     * Waits if necessary until a permit became available.
     *
     * @return permit id
     * @throws InterruptedException if the current thread is interrupted
     */
    String acquire() throws InterruptedException;
    
    /**
     * Acquires defined amount of <code>permits</code>.
     * Waits if necessary until enough permits became available.
     *
     * @param permits the number of permits to acquire
     * @return permits ids
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if <code>permits</code> is negative
     */
    List<String> acquire(int permits) throws InterruptedException;
    
    /**
     * Acquires a permit with defined <code>leaseTime</code> and return its id.
     * Waits if necessary until a permit became available.
     *
     * @param leaseTime permit lease time
     * @param unit time unit
     * @return permit id
     * @throws InterruptedException if the current thread is interrupted
     */
    String acquire(long leaseTime, TimeUnit unit) throws InterruptedException;
    
    /**
     * Acquires defined amount of <code>permits</code> with defined <code>leaseTime</code> and returns ids.
     * Waits if necessary until enough permits became available.
     *
     * @param permits the number of permits to acquire
     * @param leaseTime permit lease time
     * @param unit time unit
     * @return permits ids
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if <code>permits</code> is negative
     */
    List<String> acquire(int permits, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Tries to acquire currently available permit and return its id.
     *
     * @return permit id if a permit was acquired and {@code null}
     *         otherwise
     */
    String tryAcquire();
    
    /**
     * Tries to acquire defined amount of currently available <code>permits</code> and returns ids.
     *
     * @param permits the number of permits to acquire
     * @return permits ids if permits were acquired and empty collection
     *         otherwise
     * @throws IllegalArgumentException if <code>permits</code> is negative
     */
    List<String> tryAcquire(int permits);
    
    /**
     * Tries to acquire currently available permit and return its id.
     * Waits up to defined <code>waitTime</code> if necessary until a permit became available.
     * 
     * @param waitTime the maximum time to wait
     * @param unit the time unit
     * @return permit id if a permit was acquired and {@code null}
     *         if the waiting time elapsed before a permit was acquired
     * @throws InterruptedException if the current thread is interrupted
     */
    String tryAcquire(long waitTime, TimeUnit unit) throws InterruptedException;

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
     * @throws InterruptedException if the current thread is interrupted
     */
    String tryAcquire(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Tries to acquire defined amount of currently available <code>permits</code>
     * with defined <code>leaseTime</code> and return ids.
     * Waits up to defined <code>waitTime</code> if necessary until enough permits became available.
     *
     * @param permits the number of permits to acquire
     * @param waitTime the maximum time to wait
     * @param leaseTime permit lease time, use -1 to make it permanent
     * @param unit the time unit
     * @return permits ids if permits were acquired and empty collection
     *         if the waiting time elapsed before permits were acquired
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if <code>permits</code> is negative
     */
    List<String> tryAcquire(int permits, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Tries to release permit by its id.
     *
     * @param permitId permit id
     * @return <code>true</code> if a permit has been released and <code>false</code>
     *         otherwise
     * @throws IllegalArgumentException if <code>permitId</code> is null
     */
    boolean tryRelease(String permitId);
    
    /**
     * Tries to release permits by their ids.
     *
     * @param permitsIds - permits ids
     * @return amount of released permits
     * @throws IllegalArgumentException if <code>permitsIds</code> is null or empty
     */
    int tryRelease(List<String> permitsIds);
    
    /**
     * Releases a permit by its id. Increases the number of available permits.
     * Throws an exception if permit id doesn't exist or has already been released.
     * 
     * @param permitId - permit id
     * @throws IllegalArgumentException if <code>permitId</code> is null
     */
    void release(String permitId);
    
    /**
     * Releases permits by their ids. Increases the number of available permits.
     * Throws an exception if permit id doesn't exist or has already been released.
     *
     * @param permitsIds - permits ids
     * @throws IllegalArgumentException if <code>permitsIds</code> is null or empty
     */
    void release(List<String> permitsIds);

    /**
     * Returns number of available permits.
     *
     * @return number of available permits
     */
    int availablePermits();

    /**
     * Returns the number of permits.
     *
     * @return number of permits
     */
    int getPermits();

    /**
     * Returns the number of acquired permits.
     *
     * @return number of acquired permits
     */
    int acquiredPermits();

    /**
     * Tries to set the initial number of available permits.
     *
     * @param permits - number of permits
     * @return <code>true</code> if permits has been set successfully, otherwise <code>false</code>.  
     */
    boolean trySetPermits(int permits);

    /**
     * Sets the number of permits to the provided value.
     * Calculates the <code>delta</code> between the given <code>permits</code> value and the
     * current number of permits, then increases the number of available permits by <code>delta</code>.
     *
     * @param permits - number of permits
     */
    void setPermits(int permits);

    /**
     * Increases or decreases the number of available permits by defined value. 
     *
     * @param permits amount of permits to add/remove
     */
    void addPermits(int permits);
 
    /**
     * Overrides and updates lease time for defined permit id.
     * 
     * @param permitId permit id
     * @param leaseTime permit lease time, use -1 to make it permanent
     * @param unit the time unit
     * @return <code>true</code> if permits has been updated successfully, otherwise <code>false</code>.
     */
    boolean updateLeaseTime(String permitId, long leaseTime, TimeUnit unit);
    
    /**
     * Returns lease time of the permitId
     *
     * @param permitId permit id
     * @return lease time in millis or -1 if no lease time specified
     * @throws IllegalArgumentException if permit id doesn't exist or has already been released.
     */
    long getLeaseTime(String permitId);
    
}
