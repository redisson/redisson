package org.redisson.liveobject.resolver;

import java.lang.annotation.Annotation;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @param <T> Field instance
 * @param <A> Annotation to resolve
 */
public interface Resolver<T, A extends Annotation, V> {

    public V resolve(T value, A index);

}
