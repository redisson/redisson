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

/**
 * Stream Message ID object 
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamMessageId {

    /**
     * Defines id to receive Stream entries never delivered to any other consumer.
     * <p> 
     * Used in {@link RStream#readGroup} methods
     */
    public static final StreamMessageId NEVER_DELIVERED = new StreamMessageId(-1);
    
    /**
     * Defines minimal id. Used in {@link RStream#range} methods
     */
    public static final StreamMessageId MIN = new StreamMessageId(-1);
    
    /**
     * Defines maximal id. Used in {@link RStream#range} methods
     */
    public static final StreamMessageId MAX = new StreamMessageId(-1);
    
    /**
     * Defines id to receive Stream entries added since method invocation.
     * <p>
     * Used in {@link RStream#read}, {@link RStream#createGroup} methods
     */
    public static final StreamMessageId NEWEST = new StreamMessageId(-1);

    /**
     * Defines id to receive all Stream entries.
     * <p>
     * Used in {@link RStream#read}, {@link RStream#createGroup} methods
     */
    public static final StreamMessageId ALL = new StreamMessageId(-1);
    
    private long id0;
    private long id1;
    
    public StreamMessageId(long id0) {
        super();
        this.id0 = id0;
    }

    public StreamMessageId(long id0, long id1) {
        super();
        this.id0 = id0;
        this.id1 = id1;
    }
    
    /**
     * Returns first part of ID
     * 
     * @return first part of ID
     */
    public long getId0() {
        return id0;
    }

    /**
     * Returns second part of ID
     * 
     * @return second part of ID
     */
    public long getId1() {
        return id1;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (id0 ^ (id0 >>> 32));
        result = prime * result + (int) (id1 ^ (id1 >>> 32));
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
        StreamMessageId other = (StreamMessageId) obj;
        if (id0 != other.id0)
            return false;
        if (id1 != other.id1)
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        if (this == NEVER_DELIVERED) {
            return ">";
        }
        if (this == NEWEST) {
            return "$";
        }
        if (this == MIN) {
            return "-";
        }
        if (this == MAX) {
            return "+";
        }
        if (this == ALL) {
            return "0";
        }

        return id0 + "-" + id1;
    }
    
}
