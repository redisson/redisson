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
package org.redisson.api.options;

import org.redisson.api.RType;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class KeysScanParams implements KeysScanOptions {

    private int limit;
    private String pattern;
    private int chunkSize;
    private RType type;

    @Override
    public KeysScanOptions limit(int value) {
        this.limit = value;
        return this;
    }

    @Override
    public KeysScanOptions pattern(String value) {
        this.pattern = value;
        return this;
    }

    @Override
    public KeysScanOptions chunkSize(int value) {
        this.chunkSize = value;
        return this;
    }

    @Override
    public KeysScanOptions type(RType value) {
        this.type = value;
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public String getPattern() {
        return pattern;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public RType getType() {
        return type;
    }
}
