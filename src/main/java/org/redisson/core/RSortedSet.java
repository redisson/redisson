package org.redisson.core;

import java.util.Comparator;
import java.util.SortedSet;

public interface RSortedSet<V> extends SortedSet<V>, RObject {

    /**
     * Sets new comparator only if current set is empty
     *
     * @param comparator 
     * @return <code>true</code> if new comparator setted
     *         <code>false</code> otherwise
     */
    boolean trySetComparator(Comparator<V> comparator);

}
