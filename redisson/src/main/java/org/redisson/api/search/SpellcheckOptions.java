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
import java.util.Collections;
import java.util.List;

/**
 * Spellcheck options for
 * {@link org.redisson.api.RSearch#spellcheck(String, String, SpellcheckOptions)} method
 *
 * @author Nikita Koksharov
 *
 */
public final class SpellcheckOptions {

    private Integer distance;
    private Integer dialect;
    private List<String> includedTerms = Collections.emptyList();
    private List<String> excludedTerms = Collections.emptyList();
    private String excludedDictionary;
    private String includedDictionary;

    private SpellcheckOptions() {
    }

    public static SpellcheckOptions defaults() {
        return new SpellcheckOptions();
    }

    /**
     * Defines maximum Levenshtein distance for spelling suggestions.
     * Allowed values from 1 to 4.
     * <p>
     * Default is <code>1</code>
     *
     * @param distance maximum Levenshtein distance
     * @return options object
     */
    public SpellcheckOptions distance(Integer distance) {
        this.distance = distance;
        return this;
    }

    /**
     * Defines dialect version used for query execution.
     *
     * @param dialect dialect version
     * @return options object
     */
    public SpellcheckOptions dialect(Integer dialect) {
        this.dialect = dialect;
        return this;
    }

    /**
     * Defines <code>includedTerms</code> of a custom <code>dictionary</code>.
     *
     * @param dictionary custom dictionary
     * @param includedTerms included terms
     * @return options object
     */
    public SpellcheckOptions includedTerms(String dictionary, String... includedTerms) {
        this.includedDictionary = dictionary;
        this.includedTerms = Arrays.asList(includedTerms);
        return this;
    }

    /**
     * Defines <code>excludedTerms</code> of a custom <code>dictionary</code>.
     *
     * @param dictionary custom dictionary
     * @param excludedTerms excluded terms
     * @return options object
     */
    public SpellcheckOptions excludedTerms(String dictionary, String... excludedTerms) {
        this.excludedDictionary = dictionary;
        this.excludedTerms = Arrays.asList(excludedTerms);
        return this;
    }

    public Integer getDistance() {
        return distance;
    }

    public Integer getDialect() {
        return dialect;
    }

    public List<String> getIncludedTerms() {
        return includedTerms;
    }

    public List<String> getExcludedTerms() {
        return excludedTerms;
    }

    public String getExcludedDictionary() {
        return excludedDictionary;
    }

    public String getIncludedDictionary() {
        return includedDictionary;
    }
}
