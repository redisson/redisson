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
package org.redisson.api.array;

/**
 * Arguments object for array grep operation.
 *
 * @author lamnt2008
 *
 */
public interface ArrayGrepArgs {

    /**
     * Defines exact match predicate.
     *
     * @param value value
     * @return arguments object
     */
    static ArrayGrepArgs exact(Object value) {
        return new ArrayGrepParams().withExact(value);
    }

    /**
     * Defines substring match predicate.
     *
     * @param value value
     * @return arguments object
     */
    static ArrayGrepArgs match(Object value) {
        return new ArrayGrepParams().withMatch(value);
    }

    /**
     * Defines glob match predicate.
     *
     * @param pattern pattern
     * @return arguments object
     */
    static ArrayGrepArgs glob(String pattern) {
        return new ArrayGrepParams().withGlob(pattern);
    }

    /**
     * Defines regular expression match predicate.
     *
     * @param pattern pattern
     * @return arguments object
     */
    static ArrayGrepArgs regex(String pattern) {
        return new ArrayGrepParams().withRegex(pattern);
    }

    /**
     * Defines exact match predicate.
     *
     * @param value value
     * @return arguments object
     */
    ArrayGrepArgs withExact(Object value);

    /**
     * Defines substring match predicate.
     *
     * @param value value
     * @return arguments object
     */
    ArrayGrepArgs withMatch(Object value);

    /**
     * Defines glob match predicate.
     *
     * @param pattern pattern
     * @return arguments object
     */
    ArrayGrepArgs withGlob(String pattern);

    /**
     * Defines regular expression match predicate.
     *
     * @param pattern pattern
     * @return arguments object
     */
    ArrayGrepArgs withRegex(String pattern);

    /**
     * Defines predicates to be combined with AND.
     *
     * @return arguments object
     */
    ArrayGrepArgs and();

    /**
     * Defines predicates to be combined with OR.
     *
     * @return arguments object
     */
    ArrayGrepArgs or();

    /**
     * Defines matches limit.
     *
     * @param value limit value
     * @return arguments object
     */
    ArrayGrepArgs limit(long value);

    /**
     * Defines case-insensitive matching.
     *
     * @return arguments object
     */
    ArrayGrepArgs noCase();

}
