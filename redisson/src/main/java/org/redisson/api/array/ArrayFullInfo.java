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

/**
 * Full array information object.
 * <p>
 * Extends {@link ArrayInfo} with the extended statistics returned only when
 * full information is requested through {@code RArray.getFullInfo()}.
 *
 * @author Nikita Koksharov
 *
 */
public final class ArrayFullInfo extends ArrayInfo {

    private static final long serialVersionUID = -5000606611320810658L;

    private Long denseSlices;
    private Long sparseSlices;
    private Double averageDenseSize;
    private Double averageDenseFill;
    private Double averageSparseSize;

    /**
     * Returns number of dense slices.
     *
     * @return number of dense slices
     */
    public Long getDenseSlices() {
        return denseSlices;
    }

    public void setDenseSlices(Long denseSlices) {
        this.denseSlices = denseSlices;
    }

    /**
     * Returns number of sparse slices.
     *
     * @return number of sparse slices
     */
    public Long getSparseSlices() {
        return sparseSlices;
    }

    public void setSparseSlices(Long sparseSlices) {
        this.sparseSlices = sparseSlices;
    }

    /**
     * Returns average dense slice size.
     *
     * @return average dense slice size
     */
    public Double getAverageDenseSize() {
        return averageDenseSize;
    }

    public void setAverageDenseSize(Double averageDenseSize) {
        this.averageDenseSize = averageDenseSize;
    }

    /**
     * Returns average dense slice fill ratio.
     *
     * @return average dense slice fill ratio
     */
    public Double getAverageDenseFill() {
        return averageDenseFill;
    }

    public void setAverageDenseFill(Double averageDenseFill) {
        this.averageDenseFill = averageDenseFill;
    }

    /**
     * Returns average sparse slice size.
     *
     * @return average sparse slice size
     */
    public Double getAverageSparseSize() {
        return averageSparseSize;
    }

    public void setAverageSparseSize(Double averageSparseSize) {
        this.averageSparseSize = averageSparseSize;
    }

    @Override
    public String toString() {
        return "ArrayFullInfo{" +
                "count=" + getCount() +
                ", length=" + getLength() +
                ", nextInsertIndex=" + getNextInsertIndex() +
                ", slices=" + getSlices() +
                ", directorySize=" + getDirectorySize() +
                ", superDirectoryEntries=" + getSuperDirectoryEntries() +
                ", sliceSize=" + getSliceSize() +
                ", denseSlices=" + denseSlices +
                ", sparseSlices=" + sparseSlices +
                ", averageDenseSize=" + averageDenseSize +
                ", averageDenseFill=" + averageDenseFill +
                ", averageSparseSize=" + averageSparseSize +
                '}';
    }
}
