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

import java.util.List;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class IndexInfo {

    private String name;

    private Map<String, Object> options;

    private Map<String, Object> definition;

    private List<Map<String, Object>> attributes;

    private Map<String, Object> gcStats;

    private Map<String, Object> cursorStats;

    private Map<String, Object> dialectStats;

    private Long docs;

    private Long maxDocId;

    private Long terms;

    private Long records;

    private Double invertedSize;

    private Double vectorIndexSize;

    private Double totalInvertedIndexBlocks;

    private Double offsetVectorsSize;

    private Double docTableSize;

    private Double sortableValuesSize;

    private Double keyTableSize;

    private Double recordsPerDocAverage;

    private Double bytesPerRecordAverage;

    private Double offsetsPerTermAverage;
    private Long offsetBitsPerRecordAverage;

    private Long hashIndexingFailures;

    private Double totalIndexingTime;

    private Long indexing;

    private Double percentIndexed;

    private Long numberOfUses;

    public String getName() {
        return name;
    }

    public IndexInfo setName(String name) {
        this.name = name;
        return this;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public IndexInfo setOptions(Map<String, Object> options) {
        this.options = options;
        return this;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    public IndexInfo setDefinition(Map<String, Object> definition) {
        this.definition = definition;
        return this;
    }

    public List<Map<String, Object>> getAttributes() {
        return attributes;
    }

    public IndexInfo setAttributes(List<Map<String, Object>> attributes) {
        this.attributes = attributes;
        return this;
    }

    public Map<String, Object> getGcStats() {
        return gcStats;
    }

    public IndexInfo setGcStats(Map<String, Object> gcStats) {
        this.gcStats = gcStats;
        return this;
    }

    public Map<String, Object> getCursorStats() {
        return cursorStats;
    }

    public IndexInfo setCursorStats(Map<String, Object> cursorStats) {
        this.cursorStats = cursorStats;
        return this;
    }

    public Map<String, Object> getDialectStats() {
        return dialectStats;
    }

    public IndexInfo setDialectStats(Map<String, Object> dialectStats) {
        this.dialectStats = dialectStats;
        return this;
    }

    public Long getDocs() {
        return docs;
    }

    public IndexInfo setDocs(Long docs) {
        this.docs = docs;
        return this;
    }

    public Long getMaxDocId() {
        return maxDocId;
    }

    public IndexInfo setMaxDocId(Long maxDocId) {
        this.maxDocId = maxDocId;
        return this;
    }

    public Long getTerms() {
        return terms;
    }

    public IndexInfo setTerms(Long terms) {
        this.terms = terms;
        return this;
    }

    public Long getRecords() {
        return records;
    }

    public IndexInfo setRecords(Long records) {
        this.records = records;
        return this;
    }

    public Double getInvertedSize() {
        return invertedSize;
    }

    public IndexInfo setInvertedSize(Double invertedSize) {
        this.invertedSize = invertedSize;
        return this;
    }

    public Double getVectorIndexSize() {
        return vectorIndexSize;
    }

    public IndexInfo setVectorIndexSize(Double vectorIndexSize) {
        this.vectorIndexSize = vectorIndexSize;
        return this;
    }

    public Double getTotalInvertedIndexBlocks() {
        return totalInvertedIndexBlocks;
    }

    public IndexInfo setTotalInvertedIndexBlocks(Double totalInvertedIndexBlocks) {
        this.totalInvertedIndexBlocks = totalInvertedIndexBlocks;
        return this;
    }

    public Double getOffsetVectorsSize() {
        return offsetVectorsSize;
    }

    public IndexInfo setOffsetVectorsSize(Double offsetVectorsSize) {
        this.offsetVectorsSize = offsetVectorsSize;
        return this;
    }

    public Double getDocTableSize() {
        return docTableSize;
    }

    public IndexInfo setDocTableSize(Double docTableSize) {
        this.docTableSize = docTableSize;
        return this;
    }

    public Double getSortableValuesSize() {
        return sortableValuesSize;
    }

    public IndexInfo setSortableValuesSize(Double sortableValuesSize) {
        this.sortableValuesSize = sortableValuesSize;
        return this;
    }

    public Double getKeyTableSize() {
        return keyTableSize;
    }

    public IndexInfo setKeyTableSize(Double keyTableSize) {
        this.keyTableSize = keyTableSize;
        return this;
    }

    public Double getRecordsPerDocAverage() {
        return recordsPerDocAverage;
    }

    public IndexInfo setRecordsPerDocAverage(Double recordsPerDocAverage) {
        this.recordsPerDocAverage = recordsPerDocAverage;
        return this;
    }

    public Double getBytesPerRecordAverage() {
        return bytesPerRecordAverage;
    }

    public IndexInfo setBytesPerRecordAverage(Double bytesPerRecordAverage) {
        this.bytesPerRecordAverage = bytesPerRecordAverage;
        return this;
    }

    public Double getOffsetsPerTermAverage() {
        return offsetsPerTermAverage;
    }

    public IndexInfo setOffsetsPerTermAverage(Double offsetsPerTermAverage) {
        this.offsetsPerTermAverage = offsetsPerTermAverage;
        return this;
    }

    public Long getOffsetBitsPerRecordAverage() {
        return offsetBitsPerRecordAverage;
    }

    public IndexInfo setOffsetBitsPerRecordAverage(Long offsetBitsPerRecordAverage) {
        this.offsetBitsPerRecordAverage = offsetBitsPerRecordAverage;
        return this;
    }

    public Long getHashIndexingFailures() {
        return hashIndexingFailures;
    }

    public IndexInfo setHashIndexingFailures(Long hashIndexingFailures) {
        this.hashIndexingFailures = hashIndexingFailures;
        return this;
    }

    public Double getTotalIndexingTime() {
        return totalIndexingTime;
    }

    public IndexInfo setTotalIndexingTime(Double totalIndexingTime) {
        this.totalIndexingTime = totalIndexingTime;
        return this;
    }

    public Long getIndexing() {
        return indexing;
    }

    public IndexInfo setIndexing(Long indexing) {
        this.indexing = indexing;
        return this;
    }

    public Double getPercentIndexed() {
        return percentIndexed;
    }

    public IndexInfo setPercentIndexed(Double percentIndexed) {
        this.percentIndexed = percentIndexed;
        return this;
    }

    public Long getNumberOfUses() {
        return numberOfUses;
    }

    public IndexInfo setNumberOfUses(Long numberOfUses) {
        this.numberOfUses = numberOfUses;
        return this;
    }

    @Override
    public String toString() {
        return "IndexInfo{" +
                "name='" + name + '\'' +
                ", options=" + options +
                ", definition=" + definition +
                ", attributes=" + attributes +
                ", gcStats=" + gcStats +
                ", cursorStats=" + cursorStats +
                ", dialectStats=" + dialectStats +
                ", docs=" + docs +
                ", maxDocId=" + maxDocId +
                ", terms=" + terms +
                ", records=" + records +
                ", invertedSize=" + invertedSize +
                ", vectorIndexSize=" + vectorIndexSize +
                ", totalInvertedIndexBlocks=" + totalInvertedIndexBlocks +
                ", offsetVectorsSize=" + offsetVectorsSize +
                ", docTableSize=" + docTableSize +
                ", sortableValuesSize=" + sortableValuesSize +
                ", keyTableSize=" + keyTableSize +
                ", recordsPerDocAverage=" + recordsPerDocAverage +
                ", bytesPerRecordAverage=" + bytesPerRecordAverage +
                ", offsetsPerTermAverage=" + offsetsPerTermAverage +
                ", offsetBitsPerRecordAverage=" + offsetBitsPerRecordAverage +
                ", hashIndexingFailures=" + hashIndexingFailures +
                ", totalIndexingTime=" + totalIndexingTime +
                ", indexing=" + indexing +
                ", percentIndexed=" + percentIndexed +
                ", numberOfUses=" + numberOfUses +
                '}';
    }
}
