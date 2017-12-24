/**
 * Copyright 2016 Nikita Koksharov
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

import java.net.InetSocketAddress;
import java.net.URI;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class URIBuilder {
    
    public static URI create(String uri) {
        URI u = URI.create(uri);
        //Let's assuming most of the time it is OK.
        if (u.getHost() != null) {
            return u;
        }
        String s = uri.substring(0, uri.lastIndexOf(":"))
                .replaceFirst("redis://", "")
                .replaceFirst("rediss://", "");
        //Assuming this is an IPv6 format, other situations will be handled by
        //Netty at a later stage.
        return URI.create(uri.replace(s, "[" + s + "]"));
    }
    
    public static boolean compare(InetSocketAddress entryAddr, URI addr) {
        if (((entryAddr.getHostName() != null && entryAddr.getHostName().equals(addr.getHost()))
                || entryAddr.getAddress().getHostAddress().equals(addr.getHost()))
                    && entryAddr.getPort() == addr.getPort()) {
            return true;
        }
        return false;
    }
    
}
