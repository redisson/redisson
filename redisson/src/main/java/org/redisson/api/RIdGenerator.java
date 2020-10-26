package org.redisson.api;

/**
 * Id generator of <code>Long</code> type numbers.
 * Returns unique numbers but not monotonically increased.
 *
 * @author Nikita Koksharov
 *
 */
public interface RIdGenerator extends RExpirable, RIdGeneratorAsync {

    /**
     * Initializes Id generator params.
     *
     * @param value - initial value
     * @param allocationSize - values range allocation size
     * @return <code>true</code> if Id generator initialized
     *         <code>false</code> if Id generator already initialized
     */
    boolean tryInit(long value, long allocationSize);

    /**
     * Returns next unique number but not monotonically increased
     *
     * @return number
     */
    long nextId();

}
