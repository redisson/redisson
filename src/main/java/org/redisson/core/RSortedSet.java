package org.redisson.core;

import java.util.Comparator;
import java.util.SortedSet;

public interface RSortedSet<V> extends SortedSet<V>, RObject {

    boolean trySetComparator(Comparator<V> comparator);

}
