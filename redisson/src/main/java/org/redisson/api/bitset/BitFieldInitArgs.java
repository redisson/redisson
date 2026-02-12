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
package org.redisson.api.bitset;

/**
 * Arguments object for BITFIELD command.
 *
 * @author Su Ko
 *
 */
public interface BitFieldInitArgs {

    /**
     * Adds OVERFLOW subcommand.
     * Sets overflow behavior for subsequent SET/INCRBY operations until the next OVERFLOW.
     *
     * @param overflow overflow behavior
     * @return arguments object
     */
    BitFieldInitArgs overflow(BitFieldOverflow overflow);

    /**
     * Adds GET subcommand for signed value.
     * Returns the value stored at the given encoding/offset.
     *
     * @param size size of signed number up to 64 bits
     * @param offset offset created by {@link BitOffset#bit(long)} or {@link BitOffset#index(long)}
     * @return arguments object
     */
    BitFieldArgs getSigned(int size, BitOffset offset);

    /**
     * Adds GET subcommand for unsigned value.
     * Returns the value stored at the given encoding/offset.
     *
     * @param size size of unsigned number up to 63 bits
     * @param offset offset created by {@link BitOffset#bit(long)} or {@link BitOffset#index(long)}
     * @return arguments object
     */
    BitFieldArgs getUnsigned(int size, BitOffset offset);

    /**
     * Adds SET subcommand for signed value.
     * Sets the value and returns the previous value
     * may return null if OVERFLOW FAIL is set.
     *
     * @param size size of signed number up to 64 bits
     * @param offset offset created by {@link BitOffset#bit(long)} or {@link BitOffset#index(long)}
     * @param value value to set
     * @return arguments object
     */
    BitFieldArgs setSigned(int size, BitOffset offset, long value);

    /**
     * Adds SET subcommand for unsigned value.
     * Sets the value and returns the previous value
     * may return null if OVERFLOW FAIL is set.
     *
     * @param size size of unsigned number up to 63 bits
     * @param offset offset created by {@link BitOffset#bit(long)} or {@link BitOffset#index(long)}
     * @param value value to set
     * @return arguments object
     */
    BitFieldArgs setUnsigned(int size, BitOffset offset, long value);

    /**
     * Adds INCRBY subcommand for signed value.
     * Increments by the given amount and returns the new value
     * may return null if OVERFLOW FAIL is set.
     *
     * @param size size of signed number up to 64 bits
     * @param offset offset created by {@link BitOffset#bit(long)} or {@link BitOffset#index(long)}
     * @param increment increment value
     * @return arguments object
     */
    BitFieldArgs incrementSignedBy(int size, BitOffset offset, long increment);

    /**
     * Adds INCRBY subcommand for unsigned value.
     * Increments by the given amount and returns the new value
     * may return null if OVERFLOW FAIL is set.
     *
     * @param size size of unsigned number up to 63 bits
     * @param offset offset created by {@link BitOffset#bit(long)} or {@link BitOffset#index(long)}
     * @param increment increment value
     * @return arguments object
     */
    BitFieldArgs incrementUnsignedBy(int size, BitOffset offset, long increment);
}
