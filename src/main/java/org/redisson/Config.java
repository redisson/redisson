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
package org.redisson;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.RedissonCodec;

public class Config {

    private RedissonCodec codec = new JsonJacksonCodec();

    private int subscriptionsPerConnection = 5;

    private int connectionPoolSize = 100;

    private String password;

    private List<URI> addresses = new ArrayList<URI>();

    public Config() {
    }

    Config(Config oldConf) {
        setCodec(oldConf.getCodec());
        setConnectionPoolSize(oldConf.getConnectionPoolSize());
        setPassword(oldConf.getPassword());
        setSubscriptionsPerConnection(oldConf.getSubscriptionsPerConnection());
        setAddresses(oldConf.getAddresses());
    }

    public void setCodec(RedissonCodec codec) {
        this.codec = codec;
    }
    public RedissonCodec getCodec() {
        return codec;
    }

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
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
    void setAddresses(List<URI> addresses) {
        this.addresses = addresses;
    }

}
