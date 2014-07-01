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
package org.redisson.core;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Interface with expiration support for Redisson objects.
 *
 * @author Nikita Koksharov
 *
 */
public interface RExpirable extends RObject {

    boolean expire(long timeToLive, TimeUnit timeUnit);

    boolean expireAt(long timestamp);

    boolean expireAt(Date timestamp);

    /**
     * Remove the existing timeout of Redisson object
     *
     * @return <code>true</code> if timeout was removed
     *         <code>false</code> if object does not exist or does not have an associated timeout
     */
    boolean clearExpire();

    /**
     * Remaining time to live of Redisson object that has a timeout
     *
     * @return time in seconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    long remainTimeToLive();

}
