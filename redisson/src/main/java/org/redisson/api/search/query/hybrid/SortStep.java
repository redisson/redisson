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
package org.redisson.api.search.query.hybrid;

import org.redisson.api.SortOrder;

/**
 * Sort step for configuring result ordering.
 *
 * @author Nikita Koksharov
 */
public interface SortStep extends HybridQueryArgs {

    /**
     * Sorts the final results by specified fields.
     *
     * @param fieldName field name
     * @param order sorting order
     * @return hybrid query args for further configuration
     */
    HybridQueryArgs sortBy(String fieldName, SortOrder order);

    /**
     * Disables sorting of results.
     *
     * @return hybrid query args for further configuration
     */
    HybridQueryArgs noSort();

}