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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class GroupParams implements GroupBy {

    private List<String> fieldNames;
    private List<Reducer> reducers = Collections.emptyList();

    GroupParams(List<String> fieldNames) {
        this.fieldNames = fieldNames;
    }

    @Override
    public GroupBy reducers(Reducer... reducers) {
        this.reducers = Arrays.asList(reducers);
        return this;
    }

    public List<String> getFieldNames() {
        return fieldNames;
    }

    public List<Reducer> getReducers() {
        return reducers;
    }
}
