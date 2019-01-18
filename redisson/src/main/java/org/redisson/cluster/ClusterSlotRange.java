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
package org.redisson.cluster;

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
    
    public int size() {
        return endSlot - startSlot + 1;
    }

    public boolean isOwn(int slot) {
        return slot >= startSlot && slot <= endSlot;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + endSlot;
        result = prime * result + startSlot;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClusterSlotRange other = (ClusterSlotRange) obj;
        if (endSlot != other.endSlot)
            return false;
        if (startSlot != other.startSlot)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "[" + startSlot + "-" + endSlot + "]";
    }



}
