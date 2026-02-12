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
package org.redisson.api.search.query;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class ReturnAttribute {

    private final String identifier;
    private final String property;

    public ReturnAttribute(String identifier) {
        this(identifier, null);
    }

    public ReturnAttribute(String identifier, String property) {
        this.identifier = identifier;
        this.property = property;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getProperty() {
        return property;
    }
}
