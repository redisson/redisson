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
package org.redisson.api.cuckoofilter;

import java.util.Collection;

/**
 * Arguments for cuckoo filter bulk add operations.
 *
 * <p>Usage example:
 * <pre>
 *     Set&lt;String&gt; added = filter.add(
 *         CuckooFilterAddArgs.&lt;String&gt;items(List.of("a", "b", "c"))
 *                 .capacity(50000)
 *                 .noCreate());
 * </pre>
 *
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public interface CuckooFilterAddArgs<V> {

    /**
     * Creates arguments with the specified items to insert.
     *
     * @param items elements to insert
     * @param <V>   element type
     * @return arguments instance
     */
    static <V> CuckooFilterAddArgs<V> items(Collection<V> items) {
        return new CuckooFilterAddArgsImpl<>(items);
    }

    /**
     * Defines the desired capacity if the filter
     * is auto-created by this command.
     *
     * @param capacity filter capacity for auto-creation
     * @return arguments instance
     */
    CuckooFilterAddArgs<V> capacity(long capacity);

    /**
     * Prevents auto-creation of the filter.
     * The command will fail if the filter does not already exist.
     *
     * @return arguments instance
     */
    CuckooFilterAddArgs<V> noCreate();

}
