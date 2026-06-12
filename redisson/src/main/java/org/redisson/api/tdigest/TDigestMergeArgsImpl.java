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

import java.util.Collection;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TDigestMergeArgsImpl implements TDigestMergeArgs {

    final Collection<String> keys;
    Integer compression;
    boolean override;

    TDigestMergeArgsImpl(Collection<String> keys) {
        this.keys = keys;
    }

    @Override
    public TDigestMergeArgs compression(int compression) {
        this.compression = compression;
        return this;
    }

    @Override
    public TDigestMergeArgs override() {
        this.override = true;
        return this;
    }

    public Collection<String> getKeys() {
        return keys;
    }

    public Integer getCompression() {
        return compression;
    }

    public boolean isOverride() {
        return override;
    }
}
