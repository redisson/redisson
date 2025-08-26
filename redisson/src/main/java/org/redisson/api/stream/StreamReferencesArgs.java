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
package org.redisson.api.stream;

/**
 *
 * @author seakider
 *
 */
public interface StreamReferencesArgs<T> {
    /**
     * Defines DELREF reference policy for consumer groups when trimming.
     * When trimming, removes all references from consumer groups’ PEL
     *
     * Requires <b>Redis 8.2.0 and higher.</b>
     *
     * @return arguments object
     */
    T removeReferences();

    /**
     * Defines KEEPREF reference policy for consumer groups when trimming.
     * When trimming, preserves references in consumer groups’ PEL
     *
     * Requires <b>Redis 8.2.0 and higher.</b>
     *
     * @return arguments object
     */
    T keepReferences();

    /**
     * Defines ACKED reference policy for consumer groups when trimming.
     * When trimming, only removes entries acknowledged by all consumer groups
     *
     * Requires <b>Redis 8.2.0 and higher.</b>
     *
     * @return arguments object
     */
    T removeAcknowledgedOnly();
}
