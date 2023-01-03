/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisURI {

    private final boolean ssl;
    private final String host;
    private final int port;
    private String username;
    private String password;

    public RedisURI(String scheme, String host, int port) {
        this.ssl = "rediss".equals(scheme);
        this.host = host;
        this.port = port;
    }

    public RedisURI(String uri) {
        if (!uri.startsWith("redis://")
                && !uri.startsWith("rediss://")) {
            throw new IllegalArgumentException("Redis url should start with redis:// or rediss:// (for SSL connection)");
        }

        String urlHost = parseUrl(uri);

        try {
            URL url = new URL(urlHost);
            if (url.getUserInfo() != null) {
                String[] details = url.getUserInfo().split(":", 2);
                if (details.length == 2) {
                    if (!details[0].isEmpty()) {
                        username = details[0];
                    }
                    password = details[1];
                }
            }
            host = url.getHost();
            port = url.getPort();
            ssl = uri.startsWith("rediss://");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String parseUrl(String uri) {
        String urlHost = uri.replaceFirst("redis://", "http://").replaceFirst("rediss://", "http://");
        String ipV6Host = uri.substring(uri.indexOf("://")+3, uri.lastIndexOf(":"));
        if (ipV6Host.contains("@")) {
            ipV6Host = ipV6Host.split("@")[1];
        }
        if (ipV6Host.contains(":")) {
            urlHost = urlHost.replace(ipV6Host, "[" + ipV6Host + "]");
        }
        return urlHost;
    }

    public String getScheme() {
        if (ssl) {
            return "rediss";
        }
        return "redis";
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isSsl() {
        return ssl;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isIP() {
        return NetUtil.createByteArrayFromIpAddressString(host) != null;
    }

    private String trimIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }
    
    public boolean equals(InetSocketAddress entryAddr) {
        if (((entryAddr.getHostName() != null && entryAddr.getHostName().equals(trimIpv6Brackets(getHost())))
                || entryAddr.getAddress().getHostAddress().equals(trimIpv6Brackets(getHost())))
                && entryAddr.getPort() == getPort()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedisURI redisURI = (RedisURI) o;
        return ssl == redisURI.ssl && port == redisURI.port && Objects.equals(host, redisURI.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ssl, host, port);
    }

    @Override
    public String toString() {
        return getScheme() + "://" + trimIpv6Brackets(host) + ":" + port;
    }
    
}
