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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Nikita Koksharov
 */
public enum RType {

    /**
     * redis string type, one of :
     * - String
     * - BitMap
     * - HyperLogLog
     */
    OBJECT("string"),

    /**
     * redis hash type.
     */
    MAP("hash"),

    /**
     * redis list type.
     */
    LIST("list"),

    /**
     * redis set type.
     */
    SET("set"),

    /**
     * redis zset type. one of :
     * - ZSET
     * - GEO
     */
    ZSET("zset"),

    /**
     * redis BF type.
     */
    BLOOMFILTER("MBbloom--"),

    /**
     * redis stream type.
     */
    STREAM("stream"),

    /**
     * redis json type.
     */
    JSON("ReJSON-RL"),

    /**
     * redis none type, one of :
     * - key not exist.
     */
    NONE("none");

    private final String name;

    RType(String name) {
        this.name = name;
    }

    private static final Map<String, RType> TYPE_MAP = new HashMap<>();

    static {
        for (RType rType : RType.values()) {
            TYPE_MAP.put(rType.name, rType);
        }
    }

    public static RType parse(String name) {
        return TYPE_MAP.getOrDefault(name, null);
    }
}
