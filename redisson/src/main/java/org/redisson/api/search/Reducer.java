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
package org.redisson.api.search;

import org.redisson.api.SortOrder;
import org.redisson.api.search.aggregate.AggregationOptions;

/**
 * Reducer object
 *
 * @author Nikita Koksharov
 *
 */
public interface Reducer {

    static Reducer avg(String fieldName) {
        return new ReducerParams("AVG", fieldName);
    }

    static Reducer sum(String fieldName) {
        return new ReducerParams("SUM", fieldName);
    }

    static Reducer max(String fieldName) {
        return new ReducerParams("MAX", fieldName);
    }

    static Reducer min(String fieldName) {
        return new ReducerParams("MIN", fieldName);
    }

    static Reducer quantile(String fieldName, Double percent) {
        return new ReducerParams("QUANTILE", fieldName, percent.toString());
    }

    static Reducer count() {
        return new ReducerParams("COUNT");
    }

    static Reducer countDistinct(String fieldName) {
        return new ReducerParams("COUNT", fieldName);
    }

    static Reducer countDistinctish(String fieldName) {
        return new ReducerParams("COUNT_DISTINCTISH", fieldName);
    }

    static Reducer firstValue(String fieldName) {
        return new ReducerParams("FIRST_VALUE", fieldName);
    }

    static Reducer firstValue(String fieldName, String sortFieldName, SortOrder sortOrder) {
        return new ReducerParams("FIRST_VALUE", fieldName, "BY", sortFieldName, sortOrder.toString());
    }

    static Reducer randomSample(String fieldName, int size) {
        return new ReducerParams("FIRST_VALUE", fieldName, Integer.toString(size));
    }

    static Reducer stddev(String fieldName) {
        return new ReducerParams("STDDEV", fieldName);
    }

    static Reducer toList(String fieldName) {
        return new ReducerParams("TOLIST", fieldName);
    }

    static Reducer custom(String functionName, String... args) {
        return new ReducerParams(functionName, args);
    }

    Reducer as(String alias);

}
