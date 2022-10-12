/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
 * Maps Redisson object name.
 *
 * @author Nikita Koksharov
 *
 */
public interface NameMapper {

    /**
     * Applies map function to input <code>name</code>
     *
     * @param name - original Redisson object name
     * @return mapped name
     */
    String map(String name);

    /**
     * Applies unmap function to input mapped <code>name</code> to get original name.
     *
     * @param name - mapped name
     * @return original Redisson object name
     */
    String unmap(String name);

    /**
     * Returns input Redisson object name. Used by default
     *
     * @return NameMapper instance what returns input Redisson object name
     */
    static NameMapper direct() {
        return new DefaultNameMapper();
    }

}
