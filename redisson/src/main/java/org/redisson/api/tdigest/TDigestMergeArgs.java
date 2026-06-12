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
package org.redisson.api.tdigest;

import java.util.Arrays;
import java.util.Collection;

/**
 * Arguments for the {@code TDIGEST.MERGE} command.
 * <p>
 * The sketch the merge is invoked on is always the destination;
 * the keys supplied here are the sources merged into it.
 *
 * <p>Usage example:
 * <pre>
 *     destination.mergeWith(
 *         TDigestMergeArgs.keys("server1", "server2")
 *                 .compression(200)
 *                 .override());
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface TDigestMergeArgs {

    /**
     * Creates merge arguments for the specified source keys.
     *
     * @param keys names of the source t-digest sketches
     * @return arguments instance
     */
    static TDigestMergeArgs keys(String... keys) {
        return new TDigestMergeArgsImpl(Arrays.asList(keys));
    }

    /**
     * Creates merge arguments for the specified source keys.
     *
     * @param keys names of the source t-digest sketches
     * @return arguments instance
     */
    static TDigestMergeArgs keys(Collection<String> keys) {
        return new TDigestMergeArgsImpl(keys);
    }

    /**
     * Defines the compression of the destination sketch after the merge.
     * <p>
     * If not set, the destination keeps its current compression, or — when
     * the destination is created by this command — the maximum compression
     * among the sources is used.
     *
     * @param compression compression of the resulting sketch
     * @return arguments instance
     */
    TDigestMergeArgs compression(int compression);

    /**
     * Treats the destination as empty before merging, so its existing
     * observations are discarded and overwritten by the merged result.
     * <p>
     * Without this flag the sources are merged on top of the destination's
     * current contents.
     *
     * @return arguments instance
     */
    TDigestMergeArgs override();

}
