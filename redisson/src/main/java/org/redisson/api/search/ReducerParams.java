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
package org.redisson.api.search;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class ReducerParams implements Reducer {

    private String as;
    private String functionName;
    private List<String> args;

    ReducerParams(String functionName, String... args) {
        this.functionName = functionName;
        this.args = Arrays.asList(args);
    }

    @Override
    public Reducer as(String alias) {
        this.as = alias;
        return this;
    }

    public String getAs() {
        return as;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<String> getArgs() {
        return args;
    }
}
