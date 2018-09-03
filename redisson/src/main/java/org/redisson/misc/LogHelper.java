/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.misc;

import java.lang.reflect.Array;
import java.util.Collection;

import org.redisson.client.protocol.CommandData;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

/**
 * @author Philipp Marx
 */
public class LogHelper {

    private static final int MAX_COLLECTION_LOG_SIZE = Integer.valueOf(System.getProperty("redisson.maxCollectionLogSize", "10"));
    private static final int MAX_STRING_LOG_SIZE = Integer.valueOf(System.getProperty("redisson.maxStringLogSize", "100"));

    private LogHelper() {
    }

    public static <T> String toString(T object) {
        if (object == null) {
            return "null";
        } else if (object instanceof String) {
            return toStringString((String) object);
        } else if (object.getClass().isArray()) {
            return toArrayString(object);
        } else if (object instanceof Collection) {
            return toCollectionString((Collection<?>) object);
        } else if (object instanceof CommandData) {
            CommandData cd = (CommandData)object;
            return cd.getCommand() + ", params: " + LogHelper.toString(cd.getParams());
        } else {
            return String.valueOf(object);
        }
    }

    private static String toStringString(String string) {
        if (string.length() > MAX_STRING_LOG_SIZE) {
            return new StringBuilder(string.substring(0, MAX_STRING_LOG_SIZE)).append("...").toString();
        } else {
            return string;
        }
    }

    private static String toCollectionString(Collection<?> collection) {
        if (collection.isEmpty()) {
            return "[]";
        }

        StringBuilder b = new StringBuilder(collection.size() * 3);
        b.append('[');
        int i = 0;
        for (Object object : collection) {
            b.append(toString(object));
            i++;

            if (i == collection.size()) {
                b.append(']');
                break;
            }
            b.append(", ");
            
            if (i == MAX_COLLECTION_LOG_SIZE) {
                b.append("...]");
                break;
            }
        }
        
        return b.toString();
    }

    private static String toArrayString(Object array) {
        int length = Array.getLength(array) - 1;
        if (length == -1) {
            return "[]";
        }

        StringBuilder b = new StringBuilder(length * 3);
        b.append('[');
        for (int i = 0;; ++i) {
            b.append(toString(Array.get(array, i)));

            if (i == length) {
                return b.append(']').toString();
            }

            b.append(", ");

            if (i == MAX_COLLECTION_LOG_SIZE - 1) {
                return b.append("...]").toString();
            }
        }
    }
}
