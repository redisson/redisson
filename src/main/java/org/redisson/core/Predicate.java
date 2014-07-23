package org.redisson.core;

public interface Predicate<T> {

    boolean apply(T input);
    
}
