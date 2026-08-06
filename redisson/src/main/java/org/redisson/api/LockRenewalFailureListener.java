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

/**
 * Listener notified when a lock watchdog fails to renew a lock expiration.
 * <p>
 * A notification doesn't mean that the lock has expired or ownership has been lost.
 *
 * @author Guilherme Kauã da Silva
 */
@FunctionalInterface
public interface LockRenewalFailureListener {

    /**
     * Invoked when a lock watchdog renewal attempt fails.
     *
     * @param lockName lock name
     * @param threadId lock owner thread id
     * @param cause renewal failure
     */
    void onFailure(String lockName, long threadId, Throwable cause);

}
