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
package org.redisson.api.countmin;

import java.util.Collection;
import java.util.Objects;

/**
 * Arguments for count-min sketch merge operation.
 * <p>
 * The sketch the operation is invoked on is <b>overwritten</b> with the
 * weighted sum of the source sketches; its previous counters are discarded.
 * Naming that sketch among the sources is what turns the merge into an
 * accumulation.
 * <p>
 * All sketches, including the destination, must have the same
 * width and depth.
 *
 * <p>Usage example:
 * <pre>
 *     sketch.mergeWith(CountMinMergeArgs.sources("sketch1", "sketch2"));
 *
 *     sketch.mergeWith(CountMinMergeArgs.sources("sketch1", "sketch2")
 *                             .weights(2, 3));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface CountMinMergeArgs {

    /**
     * Creates arguments with the specified source sketch names.
     *
     * @param names names of the sketches to merge
     * @return arguments instance
     */
    static CountMinMergeArgs sources(String... names) {
        return new CountMinMergeArgsImpl(names);
    }

    /**
     * Creates arguments with the specified source sketch names.
     *
     * @param names names of the sketches to merge
     * @return arguments instance
     */
    static CountMinMergeArgs sources(Collection<String> names) {
        // Checked here rather than in the constructor: toArray() would otherwise
        // throw first, with a message naming no argument.
        Objects.requireNonNull(names, "Source sketch names can't be null");
        return new CountMinMergeArgsImpl(names.toArray(new String[0]));
    }

    /**
     * Defines the multiple of each source sketch.
     * Counts of a source sketch are multiplied by its weight
     * before being added to the destination sketch.
     * <p>
     * Default value is 1 for each source.
     *
     * @param weights weight per source sketch,
     *                in the same order and amount as the sources
     * @return arguments instance
     */
    CountMinMergeArgs weights(long... weights);

}
