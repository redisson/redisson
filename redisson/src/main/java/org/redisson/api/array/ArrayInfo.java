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
package org.redisson.api.array;

import java.io.Serializable;

/**
 * Array information object.
 * <p>
 * Holds the information that is always available for an array. Extended
 * statistics that are only populated when full information is requested are
 * exposed by {@link ArrayFullInfo}.
 *
 * @author lamnt2008
 *
 */
public class ArrayInfo implements Serializable {

    private static final long serialVersionUID = -5000606611320810658L;

    private long count;
    private long length;
    private long nextInsertIndex;
    private long slices;
    private long directorySize;
    private long superDirectoryEntries;
    private long sliceSize;

    /**
     * Returns number of values stored in array.
     *
     * @return number of values
     */
    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    /**
     * Returns array length.
     *
     * @return array length
     */
    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    /**
     * Returns next array index used by array insert operations.
     *
     * @return next array index used by array insert operations
     */
    public long getNextInsertIndex() {
        return nextInsertIndex;
    }

    public void setNextInsertIndex(long nextInsertIndex) {
        this.nextInsertIndex = nextInsertIndex;
    }

    /**
     * Returns number of slices.
     *
     * @return number of slices
     */
    public long getSlices() {
        return slices;
    }

    public void setSlices(long slices) {
        this.slices = slices;
    }

    /**
     * Returns directory size.
     *
     * @return directory size
     */
    public long getDirectorySize() {
        return directorySize;
    }

    public void setDirectorySize(long directorySize) {
        this.directorySize = directorySize;
    }

    /**
     * Returns number of super directory entries.
     *
     * @return number of super directory entries
     */
    public long getSuperDirectoryEntries() {
        return superDirectoryEntries;
    }

    public void setSuperDirectoryEntries(long superDirectoryEntries) {
        this.superDirectoryEntries = superDirectoryEntries;
    }

    /**
     * Returns slice size.
     *
     * @return slice size
     */
    public long getSliceSize() {
        return sliceSize;
    }

    public void setSliceSize(long sliceSize) {
        this.sliceSize = sliceSize;
    }

    @Override
    public String toString() {
        return "ArrayInfo{" +
                "count=" + count +
                ", length=" + length +
                ", nextInsertIndex=" + nextInsertIndex +
                ", slices=" + slices +
                ", directorySize=" + directorySize +
                ", superDirectoryEntries=" + superDirectoryEntries +
                ", sliceSize=" + sliceSize +
                '}';
    }
}
