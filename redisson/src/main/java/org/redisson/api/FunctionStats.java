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

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates information about currently running
 * Redis function and available execution engines.
 *
 * @author Nikita Koksharov
 *
 */
public class FunctionStats {

    public static class Engine {

        private final Long libraries;
        private final Long functions;

        public Engine(Long libraries, Long functions) {
            this.libraries = libraries;
            this.functions = functions;
        }

        /**
         * Returns libraries amount
         *
         * @return libraries amount
         */
        public Long getLibraries() {
            return libraries;
        }

        /**
         * Returns functions amount
         *
         * @return functions amount
         */
        public Long getFunctions() {
            return functions;
        }
    }

    public static class RunningFunction {

        private final String name;
        private final List<Object> command;
        private final Duration duration;

        public RunningFunction(String name, List<Object> command, Duration duration) {
            this.name = name;
            this.command = command;
            this.duration = duration;
        }

        /**
         * Returns name of running function
         *
         * @return name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns arguments of running function
         *
         * @return arguments
         */
        public List<Object> getCommand() {
            return command;
        }

        /**
         * Returns runtime duration of running function
         *
         * @return runtime duration
         */
        public Duration getDuration() {
            return duration;
        }
    }

    private final RunningFunction runningFunction;
    private final Map<String, Engine> engines;

    public FunctionStats(RunningFunction runningFunction, Map<String, Engine> engines) {
        this.runningFunction = runningFunction;
        this.engines = engines;
    }

    /**
     * Returns currently running fuction otherwise {@code null}
     *
     * @return running function
     */
    public RunningFunction getRunningFunction() {
        return runningFunction;
    }

    /**
     * Returns engine objects mapped by function engine name
     *
     * @return engine objects
     */
    public Map<String, Engine> getEngines() {
        return engines;
    }
}
