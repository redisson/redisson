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
package org.redisson.api.search.index;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 *
 * @author Nikita Koksharov
 *
 */
public final class IndexOptions {

    private List<String> prefix = Collections.emptyList();
    private String filter;
    private Double score;
    private String language;
    private Long temporary;
    private boolean noOffsets;
    private String languageField;
    private boolean maxTextFields;
    private boolean noFields;
    private String scoreField;
    private boolean noHL;
    private boolean noFreqs;
    private List<String> stopwords;
    private boolean skipInitialScan;
    private IndexType on;
    private byte[] payloadField;

    private IndexOptions() {
    }

    public static IndexOptions defaults() {
        return new IndexOptions();
    }

    public IndexOptions on(IndexType dataType) {
        this.on = dataType;
        return this;
    }

    public IndexOptions prefix(String... prefix) {
        return prefix(Arrays.asList(prefix));
    }

    public IndexOptions prefix(List<String> prefix) {
        this.prefix = prefix;
        return this;
    }

    public IndexOptions filter(String filter) {
        this.filter = filter;
        return this;
    }

    public IndexOptions language(String language) {
        this.language = language;
        return this;
    }

    public IndexOptions languageField(String languageField) {
        this.languageField = languageField;
        return this;
    }

    public IndexOptions score(Double score) {
        this.score = score;
        return this;
    }

    public IndexOptions scoreField(String scoreField) {
        this.scoreField = scoreField;
        return this;
    }

    public IndexOptions maxTextFields(boolean maxTextFields) {
        this.maxTextFields = maxTextFields;
        return this;
    }

    public IndexOptions noOffsets(boolean noOffsets) {
        this.noOffsets = noOffsets;
        return this;
    }

    public IndexOptions temporary(Long temporary) {
        this.temporary = temporary;
        return this;
    }

    public IndexOptions noHL(boolean noHL) {
        this.noHL = noHL;
        return this;
    }

    public IndexOptions noFields(boolean noFields) {
        this.noFields = noFields;
        return this;
    }

    public IndexOptions noFreqs(boolean noFreqs) {
        this.noFreqs = noFreqs;
        return this;
    }

    public IndexOptions stopwords(List<String> stopwords) {
        this.stopwords = stopwords;
        return this;
    }

    public IndexOptions skipInitialScan(boolean skipInitialScan) {
        this.skipInitialScan = skipInitialScan;
        return this;
    }

    public IndexOptions payloadField(byte[] payloadField) {
        this.payloadField = payloadField;
        return this;
    }

    public List<String> getPrefix() {
        return prefix;
    }

    public String getFilter() {
        return filter;
    }

    public Double getScore() {
        return score;
    }

    public String getLanguage() {
        return language;
    }

    public Long getTemporary() {
        return temporary;
    }

    public boolean isNoOffsets() {
        return noOffsets;
    }

    public String getLanguageField() {
        return languageField;
    }

    public boolean isMaxTextFields() {
        return maxTextFields;
    }

    public boolean isNoFields() {
        return noFields;
    }

    public String getScoreField() {
        return scoreField;
    }

    public boolean isNoHL() {
        return noHL;
    }

    public boolean isNoFreqs() {
        return noFreqs;
    }

    public List<String> getStopwords() {
        return stopwords;
    }

    public boolean isSkipInitialScan() {
        return skipInitialScan;
    }

    public IndexType getOn() {
        return on;
    }

    public byte[] getPayloadField() {
        return payloadField;
    }
}
