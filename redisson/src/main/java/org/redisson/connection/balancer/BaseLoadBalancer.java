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
package org.redisson.connection.balancer;

import org.redisson.connection.ClientConnectionsEntry;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public abstract class BaseLoadBalancer implements LoadBalancer {

    private Pattern pattern;

    /**
     * Defines a regular expression pattern to filter hostnames
     *
     * @param value regular expression
     */
    public void setRegex(String value) {
        this.pattern = Pattern.compile(value);
    }

    protected List<ClientConnectionsEntry> filter(List<ClientConnectionsEntry> entries) {
        if (pattern == null) {
            return entries;
        }
        return entries.stream().filter(e ->
                        pattern.matcher(e.getClient().getAddr().getHostName()).matches())
                .collect(Collectors.toList());
    }

}
