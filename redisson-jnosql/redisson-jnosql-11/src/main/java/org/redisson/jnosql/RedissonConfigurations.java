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
package org.redisson.jnosql;

import java.util.function.Supplier;

/**
 * Settings read by {@link RedissonKeyValueConfiguration}.
 * <p>
 * Redisson configuration can also be defined as flat properties prefixed with {@code jnosql.redisson.},
 * for example {@code jnosql.redisson.singleServerConfig.address}.
 *
 * @author Nikita Koksharov
 *
 */
public enum RedissonConfigurations implements Supplier<String> {

    /**
     * Location of Redisson YAML configuration. Either a file path,
     * or {@code classpath:} followed by a resource name.
     */
    FILE("jnosql.redisson.file");

    private final String configuration;

    RedissonConfigurations(String configuration) {
        this.configuration = configuration;
    }

    @Override
    public String get() {
        return configuration;
    }

}
