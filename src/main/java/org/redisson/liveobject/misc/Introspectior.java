package org.redisson.liveobject.misc;

import io.netty.util.internal.PlatformDependent;
import java.lang.annotation.Annotation;
import java.util.concurrent.ConcurrentMap;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatchers;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class Introspectior {

    public static TypeDescription.ForLoadedType getTypeDescription(Class c) {
        return new TypeDescription.ForLoadedType(c);
    }

    public static MethodDescription getMethodDescription(Class c, String method) {
        if (method == null || method.isEmpty()) {
            return null;
        }
        return getTypeDescription(c)
                .getDeclaredMethods()
                .filter(ElementMatchers.hasMethodName(method))
                .getOnly();
    }

    public static FieldDescription getFieldDescription(Class c, String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        return getTypeDescription(c)
                .getDeclaredFields()
                .filter(ElementMatchers.named(field))
                .getOnly();
    }

    public static FieldList<FieldDescription.InDefinedShape> getFieldsWithAnnotation(Class c, Class<? extends Annotation> a) {
        return getTypeDescription(c)
                .getDeclaredFields()
                .filter(ElementMatchers.isAnnotatedWith(a));
    }
}
