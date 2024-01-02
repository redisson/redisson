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
package org.redisson.cluster;

import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class ClusterSlotRange {

    private final int startSlot;
    private final int endSlot;

    public ClusterSlotRange(int startSlot, int endSlot) {
        super();
        this.startSlot = startSlot;
        this.endSlot = endSlot;
    }

    public int getStartSlot() {
        return startSlot;
    }

    public int getEndSlot() {
        return endSlot;
    }

    public boolean hasSlot(int slot) {
        return slot >= startSlot && slot <= endSlot;
    }

    public int size() {
        return endSlot - startSlot + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterSlotRange that = (ClusterSlotRange) o;
        return startSlot == that.startSlot && endSlot == that.endSlot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startSlot, endSlot);
    }

    @Override
    public String toString() {
        return "[" + startSlot + "-" + endSlot + "]";
    }



}
