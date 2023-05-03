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
package org.redisson.api.listener;

import org.redisson.api.ObjectListener;

/**
 * Redisson Object Event listener for <b>hset</b> event published by Redis.
 * <p>
 * Redis notify-keyspace-events setting should contain Eh letters
 *
 * @author Nikita Koksharov
 */
public interface MapPutListener extends ObjectListener {

    /**
     * Invoked when entry added to RMap object
     *
     * @param name object name
     */
    void onPut(String name);

}
