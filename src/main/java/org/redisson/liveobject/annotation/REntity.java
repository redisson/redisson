/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.liveobject.annotation;

import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
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

    public abstract class AbstractNamingScheme implements NamingScheme {

        protected final Codec codec;

        public AbstractNamingScheme(Codec codec) {
            this.codec = codec;
        }

    }

    public class DefaultNamingScheme extends AbstractNamingScheme implements NamingScheme {

        public static final DefaultNamingScheme INSTANCE = new DefaultNamingScheme(new JsonJacksonCodec());
        private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

        public DefaultNamingScheme(Codec codec) {
            super(codec);
        }

        @Override
        public String getName(Class cls, String idFieldName, Object id) {
            try {
                String encode = bytesToHex(codec.getMapKeyEncoder().encode(id));
                return "redisson_live_object:{class=" + cls.getName() + ", " + idFieldName + "=" + encode + "}";
            } catch (IOException ex) {
                throw new IllegalArgumentException("Unable to encode id [" + id + "] into byte[]", ex);
            }
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
            String decode = name.substring(name.indexOf("=", name.indexOf("=") + 1) + 1, name.length() - 1);
            try {
                return codec.getMapKeyDecoder().decode(Unpooled.wrappedBuffer(hexToBytes(decode)), new State(false));
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to decode [" + decode + "] into object", ex);
            }
        }

        public static String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }

        public static byte[] hexToBytes(String s) {
            int len = s.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                        + Character.digit(s.charAt(i + 1), 16));
            }
            return data;
        }
    }
}
