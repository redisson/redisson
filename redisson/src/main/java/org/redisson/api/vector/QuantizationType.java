package org.redisson.api.vector;

/**
 * Vector quantization type
 *
 * @author Nikita Koksharov
 *
 */
public enum QuantizationType {
    /**
     * No quantization
     */
    NOQUANT,

    /**
     * Binary quantization
     */
    BIN,

    /**
     * 8-bit quantization
     */
    Q8
}
