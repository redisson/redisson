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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Array grep arguments implementation.
 *
 * @author lamnt2008
 *
 */
public final class ArrayGrepParams implements ArrayGrepArgs {

    public static final class Predicate {

        public enum Type {
            EXACT,
            MATCH,
            GLOB,
            RE
        }

        private final Type type;
        private final Object value;

        Predicate(Type type, Object value) {
            this.type = type;
            this.value = value;
        }

        public Type getType() {
            return type;
        }

        public Object getValue() {
            return value;
        }

    }

    private final List<Predicate> predicates = new ArrayList<>();
    private Boolean and;
    private Long limit;
    private boolean noCase;

    @Override
    public ArrayGrepArgs withExact(Object value) {
        predicates.add(new Predicate(Predicate.Type.EXACT, Objects.requireNonNull(value, "Value can't be null")));
        return this;
    }

    @Override
    public ArrayGrepArgs withMatch(Object value) {
        predicates.add(new Predicate(Predicate.Type.MATCH, Objects.requireNonNull(value, "Value can't be null")));
        return this;
    }

    @Override
    public ArrayGrepArgs withGlob(String pattern) {
        predicates.add(new Predicate(Predicate.Type.GLOB, Objects.requireNonNull(pattern, "Pattern can't be null")));
        return this;
    }

    @Override
    public ArrayGrepArgs withRegex(String pattern) {
        predicates.add(new Predicate(Predicate.Type.RE, Objects.requireNonNull(pattern, "Pattern can't be null")));
        return this;
    }

    @Override
    public ArrayGrepArgs and() {
        and = true;
        return this;
    }

    @Override
    public ArrayGrepArgs or() {
        and = false;
        return this;
    }

    @Override
    public ArrayGrepArgs limit(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        limit = value;
        return this;
    }

    @Override
    public ArrayGrepArgs noCase() {
        noCase = true;
        return this;
    }

    public List<Predicate> getPredicates() {
        return predicates;
    }

    public Boolean getAnd() {
        return and;
    }

    public Long getLimit() {
        return limit;
    }

    public boolean isNoCase() {
        return noCase;
    }

}
