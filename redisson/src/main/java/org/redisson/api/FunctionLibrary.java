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

import java.util.List;

/**
 * Encapsulates information about Redis functions library.
 *
 * @author Nikita Koksharov
 *
 */
public class FunctionLibrary {

    public enum Flag {NO_WRITES, ALLOW_OOM, ALLOW_STALE, NO_CLUSTER}

    public static class Function {

        private final String name;
        private final String description;
        private final List<Flag> flags;

        public Function(String name, String description, List<Flag> flags) {
            this.name = name;
            this.description = description;
            this.flags = flags;
        }

        public List<Flag> getFlags() {
            return flags;
        }

        public String getDescription() {
            return description;
        }

        public String getName() {
            return name;
        }
    }


    private final String name;
    private final String engine;
    private final String code;
    private final List<Function> functions;

    public FunctionLibrary(String name, String engine, String code, List<Function> functions) {
        this.name = name;
        this.engine = engine;
        this.code = code;
        this.functions = functions;
    }

    public String getName() {
        return name;
    }

    public String getEngine() {
        return engine;
    }

    public String getCode() {
        return code;
    }

    public List<Function> getFunctions() {
        return functions;
    }
}
