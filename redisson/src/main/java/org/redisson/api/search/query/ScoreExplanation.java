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

import java.util.List;
import java.util.Objects;

/**
 * Node of a score explanation tree returned for <code>EXPLAINSCORE</code>.
 *
 * @author Nikita Koksharov
 *
 */
public final class ScoreExplanation {

    private final String description;
    private final List<ScoreExplanation> details;

    public ScoreExplanation(String description, List<ScoreExplanation> details) {
        this.description = description;
        this.details = details;
    }

    /**
     * Returns explanation text of this node, e.g. <code>text rank = 1</code>.
     *
     * @return explanation text
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns explanations of the parts this node is made of.
     *
     * @return explanations of the parts, empty for a leaf node
     */
    public List<ScoreExplanation> getDetails() {
        return details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScoreExplanation that = (ScoreExplanation) o;
        return Objects.equals(description, that.description) && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, details);
    }

    @Override
    public String toString() {
        return "ScoreExplanation{description='" + description + "', details=" + details + '}';
    }
}
