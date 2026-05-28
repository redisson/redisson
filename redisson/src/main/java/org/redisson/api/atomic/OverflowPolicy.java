package org.redisson.api.atomic;

/**
 *
 * Defines overflow policy used if the increment result is out of bounds.
 *
 */
public enum OverflowPolicy {

    /**
     * Caps the value at the lower or upper bound
     * (or the type limits when no explicit bound is given).
     * Sent to the server as the SATURATE flag.
     */
    SAT,

    /**
     * Default policy. Leaves the value and its expiration unchanged,
     * replying with the current value and a zero increment.
     */
    REJECT

}
