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
package org.redisson.api.tsnative;

import org.redisson.api.RTimeSeriesNatives;

/**
 * Arguments of {@link RTimeSeriesNatives#getAll(TSMultiGetArgs)}.
 * <p>
 * A filter expression is the module's own syntax — <code>area=32</code>,
 * <code>sensor!=(1,2)</code>, <code>region=</code> — and at least one of them must be an
 * equality, or the server refuses the command.
 *
 * @author Nikita Koksharov
 *
 */
public interface TSMultiGetArgs {

    /**
     * Selects the series to read by label filter.
     *
     * @param filters filter expressions, all of which must match
     * @return arguments object
     */
    static TSMultiGetArgs filter(String... filters) {
        return new TSMultiGetParams(filters);
    }

    /**
     * Reports the compacted value of the still-open latest bucket rather than skipping it.
     *
     * @return arguments object
     */
    TSMultiGetArgs latest();

    /**
     * Reports every label of each matched series. Mutually exclusive with
     * {@link #selectedLabels(String...)} — the server answers
     * "cannot accept WITHLABELS and SELECT_LABELS together".
     *
     * @return arguments object
     */
    TSMultiGetArgs withLabels();

    /**
     * Reports only the named labels of each matched series. A label a series does not carry is
     * reported as <code>null</code>.
     * <p>
     * Mutually exclusive with {@link #withLabels()}, and at least one label must be named.
     *
     * @param labels label names to report
     * @return arguments object
     */
    TSMultiGetArgs selectedLabels(String... labels);

}
