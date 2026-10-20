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

/**
 * Which point of an aggregation bucket its reported timestamp refers to.
 *
 * @author Nikita Koksharov
 *
 */
public enum TSBucketTimestamp {

    /**
     * Start of the bucket. The module's default.
     */
    START("start"),

    /**
     * End of the bucket.
     */
    END("end"),

    /**
     * Middle of the bucket, rounded down when it is not an integer.
     */
    MID("mid");

    private final String value;

    TSBucketTimestamp(String value) {
        this.value = value;
    }

    /**
     * Returns the token this option is written as on the wire.
     *
     * @return wire token
     */
    public String getValue() {
        return value;
    }

}
