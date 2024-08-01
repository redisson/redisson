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
package org.redisson.api.search.index;

/**
 * Text field index options.
 *
 * @author Nikita Koksharov
 *
 */
public interface TextIndex extends FieldIndex {

    /**
     * Defines the attribute associated to the field name.
     *
     * @param as the associated attribute
     * @return options object
     */
    TextIndex as(String as);

    /**
     * Defines sort mode applied to the value of this attribute.
     *
     * @param sortMode sort mode
     * @return options object
     */
    TextIndex sortMode(SortMode sortMode);

    /**
     * Defines whether to disable stemming when indexing its values.
     *
     * @return options object
     */
    TextIndex noStem();

    /**
     * Defines to not index this attribute
     *
     * @return options object
     */
    TextIndex noIndex();

    /**
     * Defines whether to keep a suffix trie with all terms which match the suffix.
     *
     * @return options object
     */
    TextIndex withSuffixTrie();

    /**
     * Defines phonetic matcher algorithm and language used for search result matching.
     *
     * @param matcher phonetic matcher algorithm and language
     * @return options object
     */
    TextIndex phonetic(PhoneticMatcher matcher);

    /**
     * Defines declares a multiplication factor value used to declare the importance
     * of this attribute when calculating result accuracy.
     *
     * @param weight multiplication factor value
     * @return options object
     */
    TextIndex weight(Double weight);

    /**
     * Defines whether to index an empty value.
     *
     * @return options object
     */
    TextIndex indexEmpty();

}
