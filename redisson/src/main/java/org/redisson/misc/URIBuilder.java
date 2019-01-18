/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class URIBuilder {

    public static URI create(String uri) {
        if (!uri.startsWith("redis://")
                && !uri.startsWith("rediss://")) {
            throw new IllegalArgumentException("Redis url should start with redis:// or rediss:// (for SSL connection)");
        }
        
        URI u = URI.create(uri);
        // Let's assuming most of the time it is OK.
        if (u.getHost() != null) {
            return u;
        }
        String s = uri.substring(0, uri.lastIndexOf(":")).replaceFirst("redis://", "").replaceFirst("rediss://", "");
        // Assuming this is an IPv6 format, other situations will be handled by
        // Netty at a later stage.
        return URI.create(uri.replace(s, "[" + s + "]"));
    }
    
    public static void patchUriObject() {
        try {
            patchUriField(35184372088832L, "L_DASH");
            patchUriField(2147483648L, "H_DASH");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    private static void patchUriField(Long maskValue, String fieldName)
            throws IOException {
        try {
            Field field = URI.class.getDeclaredField(fieldName);
            
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            
            field.setAccessible(true);
            field.setLong(null, maskValue);
        } catch (NoSuchFieldException e) {
            // skip for Android platform
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String trimIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    public static boolean compare(InetSocketAddress entryAddr, URI addr) {
        if (((entryAddr.getHostName() != null && entryAddr.getHostName().equals(trimIpv6Brackets(addr.getHost())))
                || entryAddr.getAddress().getHostAddress().equals(trimIpv6Brackets(addr.getHost())))
                && entryAddr.getPort() == addr.getPort()) {
            return true;
        }
        return false;
    }

}
