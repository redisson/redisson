package org.redisson.liveobject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author ruigu
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface REntity {

    
    Class<? extends NamingScheme> namingScheme() default DefaultNamingScheme.class;

    public interface NamingScheme {

        public String getName(Class cls, String idFieldName, Object id);

    }

    public class DefaultNamingScheme implements NamingScheme {

        public static final DefaultNamingScheme INSTANCE = new DefaultNamingScheme();

        @Override
        public String getName(Class cls, String idFieldName, Object id) {
            return "redisson_live_object:{class=" + cls.getName() + ", " + idFieldName + "=" + id.toString() + "}";
        }

    }
}
