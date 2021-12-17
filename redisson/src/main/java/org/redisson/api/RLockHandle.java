/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

public interface RLockHandle {
    /**
     * @return <code>true</code> if lock acquired otherwise <code>false</code>
     */
    Boolean isLocked();

    /**
     * Unlocks the lock
     *
     * @return void
     */
    RFuture<Void> unlockAsync();

    /**
     * Unlocks the lock. Throws {@link IllegalMonitorStateException}
     * if lock isn't locked by thread with specified <code>threadId</code>.
     *
     * @param threadId id of thread
     * @return void
     */
    RFuture<Void> unlockAsync(long threadId);
}
