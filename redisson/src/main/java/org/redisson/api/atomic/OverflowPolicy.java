package org.redisson.api.atomic;

/**
 *
 * Defines overflow policy used if the increment result is out of bounds.
 *
 */
public enum OverflowPolicy {

    /**
     * Throws an error and leaves the value unchanged.
     */
    FAIL,

    /**
     * Caps the value at the lower or upper bound.
     */
    SAT,

    /**
     * Leaves the value and its expiration unchanged.
     */
    REJECT

}
