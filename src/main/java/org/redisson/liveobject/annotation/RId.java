package org.redisson.liveobject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.redisson.liveobject.resolver.FieldValueAsIdGenerator;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface RId {
    Class generator() default FieldValueAsIdGenerator.class;
}
