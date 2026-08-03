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
package org.redisson.api.map;

import java.util.Arrays;
import java.util.List;

/**
 * Arguments object for bulk import of Map objects sharing the same field names.
 *
 * @author Nikita Koksharov
 *
 * @param <K> field type
 */
public interface MapsImportArgs<K> {

    /**
     * Defines field names shared by all imported Map objects.
     * <p>
     * Values passed to the import object are matched to these field names by position.
     *
     * @param fields field names
     * @return arguments object
     */
    @SafeVarargs
    static <K> MapsImportArgs<K> fields(K... fields) {
        return new MapsImportParams<>(Arrays.asList(fields));
    }

    /**
     * Defines field names shared by all imported Map objects.
     * <p>
     * Values passed to the import object are matched to these field names by position.
     *
     * @param fields field names
     * @return arguments object
     */
    static <K> MapsImportArgs<K> fields(List<K> fields) {
        return new MapsImportParams<>(fields);
    }

    /**
     * Defines the amount of buffered Map objects which triggers an automatic flush.
     * <p>
     * Default value is <code>500</code>.
     *
     * @param batchSize amount of Map objects
     * @return arguments object
     */
    MapsImportArgs<K> batchSize(int batchSize);

}
