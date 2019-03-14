/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.api;

import java.util.Map;

/**
 * Object containing details about Stream
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class StreamInfo<K, V> {

    public static class Entry<K, V> {
        
        final StreamMessageId id;
        final Map<K, V> data;
        
        public Entry(StreamMessageId id, Map<K, V> data) {
            this.id = id;
            this.data = data;
        }
        
        /**
         * Returns StreamMessageId of this stream entry.
         * 
         * @return StreamMessageId object
         */
        public StreamMessageId getId() {
            return id;
        }

        /**
         * Returns data stored in this stream entry
         * 
         * @return Map object
         */
        public Map<K, V> getData() {
            return data;
        }
        
    }
    
    int length;
    int radixTreeKeys;
    int radixTreeNodes;
    int groups;
    StreamMessageId lastGeneratedId;
    Entry<K, V> firstEntry;
    Entry<K, V> lastEntry;
    
    /**
     * Returns length of the stream
     * 
     * @return length of the stream
     */
    public int getLength() {
        return length;
    }
    public void setLength(int length) {
        this.length = length;
    }
    
    /**
     * Returns amount of keys allocated by Radix tree of the stream.
     * 
     * @return amount of keys
     */
    public int getRadixTreeKeys() {
        return radixTreeKeys;
    }
    public void setRadixTreeKeys(int radixTreeKeys) {
        this.radixTreeKeys = radixTreeKeys;
    }
    
    /**
     * Returns amount of nodes allocated by Radix tree of the stream.
     * 
     * @return amount of nodes
     */
    public int getRadixTreeNodes() {
        return radixTreeNodes;
    }
    public void setRadixTreeNodes(int radixTreeNodes) {
        this.radixTreeNodes = radixTreeNodes;
    }
    
    /**
     * Returns amount of groups belonging to the stream
     * 
     * @return amount of groups
     */
    public int getGroups() {
        return groups;
    }
    public void setGroups(int groups) {
        this.groups = groups;
    }
    
    /**
     * Returns last StreamMessageId used by the stream
     * 
     * @return StreamMessageId object
     */
    public StreamMessageId getLastGeneratedId() {
        return lastGeneratedId;
    }
    public void setLastGeneratedId(StreamMessageId lastGeneratedId) {
        this.lastGeneratedId = lastGeneratedId;
    }
    
    /**
     * Returns first stream entry
     * 
     * @return stream entry
     */
    public Entry<K, V> getFirstEntry() {
        return firstEntry;
    }
    public void setFirstEntry(Entry<K, V> firstEntry) {
        this.firstEntry = firstEntry;
    }

    /**
     * Returns last stream entry
     * 
     * @return stream entry
     */
    public Entry<K, V> getLastEntry() {
        return lastEntry;
    }
    public void setLastEntry(Entry<K, V> lastEntry) {
        this.lastEntry = lastEntry;
    }
    
}
