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

/**
 * Arguments for cuckoo filter initialization.
 *
 * <p>Usage example:
 * <pre>
 *     filter.init(CuckooFilterInitArgs.capacity(100000)
 *                     .bucketSize(4)
 *                     .maxIterations(500)
 *                     .expansion(2));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface CuckooFilterInitArgs {

    /**
     * Creates arguments with the specified capacity.
     *
     * @param capacity expected number of items to store in the filter
     * @return arguments instance
     */
    static CuckooFilterInitArgs capacity(long capacity) {
        return new CuckooFilterInitArgsImpl(capacity);
    }

    /**
     * Defines the number of items per bucket.
     * <p>
     * Default value is 2.
     * A higher bucket size improves fill rate but increases error rate.
     *
     * @param bucketSize number of items per bucket
     * @return arguments instance
     */
    CuckooFilterInitArgs bucketSize(long bucketSize);

    /**
     * Defines the maximum number of attempts to swap items
     * between buckets before declaring the filter full.
     * <p>
     * Default value is 20.
     *
     * @param maxIterations max number of swap attempts
     * @return arguments instance
     */
    CuckooFilterInitArgs maxIterations(long maxIterations);

    /**
     * Defines the expansion rate when the filter becomes full.
     * <p>
     * Default value is 1.
     *
     * @param expansion expansion rate
     * @return arguments instance
     */
    CuckooFilterInitArgs expansion(long expansion);

}
