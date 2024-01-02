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
 * Tag field index options.
 *
 * @author Nikita Koksharov
 *
 */
public interface TagIndex extends FieldIndex {

    /**
     * Defines the attribute associated to the field name
     *
     * @param as the associated attribute
     * @return options object
     */
    TagIndex as(String as);

    /**
     * Defines separator used for splitting the value of this attribute.
     * The separator value must be a single character.
     * <p>
     * Default is <code>,</code>
     *
     * @param separator separator value
     * @return options object
     */
    TagIndex separator(String separator);

    /**
     * Defines sort mode applied to the value of this attribute
     *
     * @param sortMode sort mode
     * @return options object
     */
    TagIndex sortMode(SortMode sortMode);

    /**
     * Defines whether to keep the original letter cases of the tags.
     * If not defined, the characters are converted to lowercase.
     *
     * @return options object
     */
    TagIndex caseSensitive();

    /**
     * Defines to not index this attribute
     *
     * @return options object
     */
    TagIndex noIndex();

    /**
     * Defines whether to keep a suffix trie with all terms which match the suffix.
     *
     * @return options object
     */
    TagIndex withSuffixTrie();

}

