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
package org.redisson.api.search.query;

import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class HighlightOptions {

    private List<String> fields;
    private String openTag;
    private String closeTag;

    private HighlightOptions() {
    }

    public static HighlightOptions defaults() {
        return new HighlightOptions();
    }

    public HighlightOptions fields(List<String> fields) {
        this.fields = fields;
        return this;
    }

    public HighlightOptions tags(String open, String close) {
        openTag = open;
        closeTag = close;
        return this;
    }

    public List<String> getFields() {
        return fields;
    }

    public String getOpenTag() {
        return openTag;
    }

    public String getCloseTag() {
        return closeTag;
    }
}
