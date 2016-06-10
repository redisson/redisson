package org.redisson.liveobject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface REntity {

    Class<? extends NamingScheme> namingScheme() default DefaultNamingScheme.class;

    Class<? extends Codec> codec() default JsonJacksonCodec.class;

    public interface NamingScheme {

        public String getName(Class cls, String idFieldName, Object id);

        public String resolveClassName(String name);

        public String resolveIdFieldName(String name);

        public Object resolveId(String name);

    }

    public class DefaultNamingScheme implements NamingScheme {

        public static final DefaultNamingScheme INSTANCE = new DefaultNamingScheme();

        @Override
        public String getName(Class cls, String idFieldName, Object id) {
            return "redisson_live_object:{class=" + cls.getName() + ", " + idFieldName + "=" + id.toString() + "}";
        }

        @Override
        public String resolveClassName(String name) {
            return name.substring("redisson_live_object:{class=".length(), name.indexOf(","));
        }

        @Override
        public String resolveIdFieldName(String name) {
            return name.substring(name.indexOf(", ") + 2, name.indexOf("=", name.indexOf("=") + 1));
        }

        @Override
        public Object resolveId(String name) {
            return name.substring(name.indexOf("=", name.indexOf("=") + 1) + 1, name.length() - 1);
        }

    }
}
