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
package org.redisson.api;

/**
 * enum type from https://redis.io/docs/latest/commands/object-encoding/
 *
 * @author seakider
 */
public enum RedisObjectEncoding {
    /**
     * Normal string encoding.
     */
    RAW("raw"),

    /**
     * Strings representing integers in a 64-bit signed interval.
     */
    INT("int"),

    /**
     * Strings with lengths up to the hardcoded limit of OBJ_ENCODING_EMBSTR_SIZE_LIMIT or 44 bytes.
     */
    EMBSTR("embstr"),

    /**
     * An old list encoding.
     * No longer used.
     */
    LINKEDLIST("linkedlist"),

    /**
     * A space-efficient encoding used for small lists.
     * Redis <= 6.2
     */
    ZIPLIST("ziplist"),

    /**
     * A space-efficient encoding used for small lists.
     * Redis >= 7.0
     */
    LISTPACK("listpack"),

    /**
     * Encoded as linkedlist of ziplists or listpacks.
     */
    QUICKLIST("quicklist"),

    /**
     * Normal set encoding.
     */
    HASHTABLE("hashtable"),

    /**
     * Small sets composed solely of integers encoding.
     */
    INTSET("intset"),

    /**
     * An old hash encoding.
     * No longer used
     */
    ZIPMAP("zipmap"),

    /**
     * Normal sorted set encoding
     */
    SKIPLIST("skiplist"),

    /**
     * Encoded as a radix tree of listpacks
     */
    STREAM("stream"),

    /**
     * Key is not exist.
     */
    NULL("nonexistence"),

    /**
     * This means redis support new type and this Enum not defined.
     */
    UNKNOWN("unknown");

    private final String type;

    RedisObjectEncoding(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static RedisObjectEncoding valueOfEncoding(Object object) {
        if (object == null) {
            return NULL;
        }
        String value = (String) object;
        for (RedisObjectEncoding encoding : RedisObjectEncoding.values()) {
            if (value.equals(encoding.getType()))
                return encoding;
        }
        return UNKNOWN;
    }
}
