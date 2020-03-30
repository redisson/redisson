/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
package org.redisson.spring.starter;

import org.redisson.config.Config;

/**
 * Callback interface that can be implemented by beans wishing to customize
 * the {@link org.redisson.api.RedissonClient} auto configuration
 *
 * @author Nikos Kakavas (https://github.com/nikakis)
 */
@FunctionalInterface
public interface RedissonAutoConfigurationCustomizer {

    /**
     * Customize the RedissonClient configuration.
     * @param configuration the {@link Config} to customize
     */
    void customize(final Config configuration);
}
