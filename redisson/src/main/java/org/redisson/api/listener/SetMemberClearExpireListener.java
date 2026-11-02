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
package org.redisson.api.listener;

import org.redisson.api.ObjectListener;

/**
 * Event listener for <b>spersist</b> event published
 * when the expiration of a set value is removed.
 * <p>
 * notify-keyspace-events setting should contain Es or Ts letters.
 * Ts delivers the value, Es only the set name.
 * <p>
 * Requires <b>Redis 8.12.0 and higher.</b>
 *
 * @see org.redisson.api.RSetCacheNative
 */
@FunctionalInterface
public interface SetMemberClearExpireListener extends ObjectListener {

    /**
     * Invoked when the expiration of a set value is cleared
     *
     * @param name object name
     * @param member the value as stored in Redis, that is, encoded by the codec
     *               of the set and read as UTF-8. Can be null for keyevent notification.
     */
    void onClearExpire(String name, String member);

}
