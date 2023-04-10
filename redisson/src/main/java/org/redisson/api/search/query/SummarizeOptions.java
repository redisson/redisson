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
public final class SummarizeOptions {

    private List<String> fields;
    private Integer fragsNum;
    private Integer fragSize;

    private String separator;

    private SummarizeOptions() {
    }

    public static SummarizeOptions defaults() {
        return new SummarizeOptions();
    }

    public SummarizeOptions fields(List<String> fields) {
        this.fields = fields;
        return this;
    }

    public SummarizeOptions fragsNum(Integer fragsNum) {
        this.fragsNum = fragsNum;
        return this;
    }

    public SummarizeOptions fragSize(Integer fragSize) {
        this.fragSize = fragSize;
        return this;
    }

    public SummarizeOptions separator(String separator) {
        this.separator = separator;
        return this;
    }

    public List<String> getFields() {
        return fields;
    }

    public Integer getFragsNum() {
        return fragsNum;
    }

    public Integer getFragSize() {
        return fragSize;
    }

    public String getSeparator() {
        return separator;
    }
}
