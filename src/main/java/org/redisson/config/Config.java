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
package org.redisson.config;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lambdaworks.redis.codec.JsonJacksonCodec;
import com.lambdaworks.redis.codec.RedisCodec;

// TODO multi addresses support
public class Config {

    private RedisCodec codec = new JsonJacksonCodec();

    private int subscriptionsPerConnection = 5;

    private int connectionPoolSize = 100;

    private int connectionPingTimeout = 5000;

    private List<URI> addresses = new ArrayList<URI>();

    public void setCodec(RedisCodec codec) {
        this.codec = codec;
    }
    public RedisCodec getCodec() {
        return codec;
    }

    public int getSubscriptionsPerConnection() {
        return subscriptionsPerConnection;
    }
    public void setSubscriptionsPerConnection(int subscriptionsPerConnection) {
        this.subscriptionsPerConnection = subscriptionsPerConnection;
    }

    public void setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }
    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPingTimeout(int connectionPingTimeout) {
        this.connectionPingTimeout = connectionPingTimeout;
    }
    public int getConnectionPingTimeout() {
        return connectionPingTimeout;
    }

    public void addAddress(String ... addressesVar) {
        for (String address : addressesVar) {
            try {
                addresses.add(new URI("//" + address));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Can't parse " + address);
            }
        }
    }
    public List<URI> getAddresses() {
        return addresses;
    }

}
