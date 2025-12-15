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
package org.redisson.api.stream;

import org.redisson.api.RStream;

import java.util.Objects;

/**
 * Stream Message ID object 
 * 
 * @author Nikita Koksharov
 *
 */
public final class StreamMessageId {

    /**
     * Defines id to receive Stream entries never delivered to any other consumer.
     * <p> 
     * Used in {@link RStream#readGroup} method
     */
    public static final StreamMessageId NEVER_DELIVERED = new StreamMessageId(-1);

    /**
     * Defines auto-generated id.
     * <p>
     * Used in {@link RStream#add} method
     */
    public static final StreamMessageId AUTO_GENERATED = new StreamMessageId(-1);

    /**
     * Defines minimal id.
     * <p>
     * Used in {@link RStream#range} methods
     */
    public static final StreamMessageId MIN = new StreamMessageId(-1);
    
    /**
     * Defines maximal id.
     * <p>
     * Used in {@link RStream#range} methods
     */
    public static final StreamMessageId MAX = new StreamMessageId(-1);
    
    /**
     * Defines id to receive Stream entries added since method invocation.
     * <p>
     * Used in {@link RStream#read}, {@link RStream#createGroup} methods
     */
    public static final StreamMessageId NEWEST = new StreamMessageId(-1);

    /**
     * Defines id to receive the latest Stream entry.
     * <p>
     * Used in {@link RStream#read}, {@link RStream#createGroup} methods
     * <p>
     * Requires Redis 7.4+
     *
     */
    public static final StreamMessageId LAST = new StreamMessageId(-1);

    /**
     * Defines id to receive all Stream entries.
     * <p>
     * Used in {@link RStream#read}, {@link RStream#createGroup} methods
     */
    public static final StreamMessageId ALL = new StreamMessageId(-1);
    
    private final long id0;
    private long id1;
    
    private boolean autogenerateSequenceId;
    
    public StreamMessageId(long id0) {
        super();
        this.id0 = id0;
    }

    public StreamMessageId(long id0, long id1) {
        super();
        this.id0 = id0;
        this.id1 = id1;
    }
    
    public StreamMessageId autogenerateSequenceId() {
        this.autogenerateSequenceId = true;
        return this;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StreamMessageId that = (StreamMessageId) o;
        return id0 == that.id0 && id1 == that.id1 && autogenerateSequenceId == that.autogenerateSequenceId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id0, id1, autogenerateSequenceId);
    }
    
    @Override
    @SuppressWarnings("AvoidInlineConditionals")
    public String toString() {
        if (this == NEVER_DELIVERED) {
            return ">";
        }
        if (this == LAST) {
            return "+";
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
        if (this == AUTO_GENERATED) {
            return "*";
        }

        return id0 + "-" + (autogenerateSequenceId ? "*" : id1);
    }
    
}
