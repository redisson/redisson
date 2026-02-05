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
package org.redisson.api.bitset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parameters for BITFIELD command.
 *
 * @author Su Ko
 *
 */
public final class BitFieldParams implements BitFieldArgs, BitFieldInitArgs {

    public enum OperationType {
        /**
         * GET subcommand.
         * Returns the value stored at the given encoding/offset.
         */
        GET,

        /**
         * SET subcommand.
         * Sets the value and returns the previous value
         * may return null if OVERFLOW FAIL is set.
         */
        SET,

        /**
         * INCRBY subcommand.
         * Increments by the given amount and returns the new value
         * may return null if OVERFLOW FAIL is set.
         */
        INCRBY,

        /**
         * OVERFLOW subcommand.
         * Sets overflow behavior for subsequent SET/INCRBY operations until the next OVERFLOW.
         */
        OVERFLOW
    }

    public static final class Operation {
        private final OperationType type;
        private final String encoding;
        private final Object offset;
        private final Long value;
        private final BitFieldOverflow overflow;

        Operation(OperationType type, String encoding, Object offset, Long value, BitFieldOverflow overflow) {
            this.type = type;
            this.encoding = encoding;
            this.offset = offset;
            this.value = value;
            this.overflow = overflow;
        }

        public OperationType getType() {
            return type;
        }

        public String getEncoding() {
            return encoding;
        }

        public Object getOffset() {
            return offset;
        }

        public Long getValue() {
            return value;
        }

        public BitFieldOverflow getOverflow() {
            return overflow;
        }
    }

    private final List<Operation> operations = new ArrayList<>();

    @Override
    public BitFieldArgs overflow(BitFieldOverflow overflow) {
        operations.add(new Operation(OperationType.OVERFLOW, null, null, null, overflow));
        return this;
    }

    @Override
    public BitFieldArgs getSigned(int size, long offset) {
        return getSigned(size, Long.valueOf(offset));
    }

    @Override
    public BitFieldArgs getSigned(int size, String offset) {
        return getSigned(size, (Object) offset);
    }

    private BitFieldArgs getSigned(int size, Object offset) {
        operations.add(new Operation(OperationType.GET, "i" + size, offset, null, null));
        return this;
    }

    @Override
    public BitFieldArgs getUnsigned(int size, long offset) {
        return getUnsigned(size, Long.valueOf(offset));
    }

    @Override
    public BitFieldArgs getUnsigned(int size, String offset) {
        return getUnsigned(size, (Object) offset);
    }

    private BitFieldArgs getUnsigned(int size, Object offset) {
        operations.add(new Operation(OperationType.GET, "u" + size, offset, null, null));
        return this;
    }

    @Override
    public BitFieldArgs setSigned(int size, long offset, long value) {
        return setSigned(size, Long.valueOf(offset), value);
    }

    @Override
    public BitFieldArgs setSigned(int size, String offset, long value) {
        return setSigned(size, (Object) offset, value);
    }

    private BitFieldArgs setSigned(int size, Object offset, long value) {
        operations.add(new Operation(OperationType.SET, "i" + size, offset, value, null));
        return this;
    }

    @Override
    public BitFieldArgs setUnsigned(int size, long offset, long value) {
        return setUnsigned(size, Long.valueOf(offset), value);
    }

    @Override
    public BitFieldArgs setUnsigned(int size, String offset, long value) {
        return setUnsigned(size, (Object) offset, value);
    }

    private BitFieldArgs setUnsigned(int size, Object offset, long value) {
        operations.add(new Operation(OperationType.SET, "u" + size, offset, value, null));
        return this;
    }

    @Override
    public BitFieldArgs incrementSignedBy(int size, long offset, long increment) {
        return incrementSignedBy(size, Long.valueOf(offset), increment);
    }

    @Override
    public BitFieldArgs incrementSignedBy(int size, String offset, long increment) {
        return incrementSignedBy(size, (Object) offset, increment);
    }

    private BitFieldArgs incrementSignedBy(int size, Object offset, long increment) {
        operations.add(new Operation(OperationType.INCRBY, "i" + size, offset, increment, null));
        return this;
    }

    @Override
    public BitFieldArgs incrementUnsignedBy(int size, long offset, long increment) {
        return incrementUnsignedBy(size, Long.valueOf(offset), increment);
    }

    @Override
    public BitFieldArgs incrementUnsignedBy(int size, String offset, long increment) {
        return incrementUnsignedBy(size, (Object) offset, increment);
    }

    private BitFieldArgs incrementUnsignedBy(int size, Object offset, long increment) {
        operations.add(new Operation(OperationType.INCRBY, "u" + size, offset, increment, null));
        return this;
    }

    public List<Operation> getOperations() {
        return Collections.unmodifiableList(operations);
    }
}
