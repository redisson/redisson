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

public class SingleServerConfig extends BaseConfig<SingleServerConfig> {

    /**
     * Redis server address
     *
     */
    private URI address;

    /**
     * Redis subscription connection pool size
     *
     */
    private int subscriptionConnectionPoolSize = 25;

    /**
     * Redis connection pool size
     */
    private int connectionPoolSize = 100;

    SingleServerConfig() {
    }

    SingleServerConfig(SingleServerConfig config) {
        super(config);
        setAddress(config.getAddress());
        setConnectionPoolSize(config.getConnectionPoolSize());
        setSubscriptionConnectionPoolSize(config.getSubscriptionConnectionPoolSize());
    }

    /**
     * Redis connection pool size
     * Default is 100
     *
     * @param connectionPoolSize
     */
    public SingleServerConfig setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
        return this;
    }
    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    /**
     * Redis subscription-connection pool size limit
     * Default is 25
     *
     * @param connectionPoolSize
     * @return
     */
    public SingleServerConfig setSubscriptionConnectionPoolSize(int subscriptionConnectionPoolSize) {
        this.subscriptionConnectionPoolSize = subscriptionConnectionPoolSize;
        return this;
    }
    public int getSubscriptionConnectionPoolSize() {
        return subscriptionConnectionPoolSize;
    }

    /**
     * Set server address. Use follow format -- host:port
     *
     * @param address
     */
    public SingleServerConfig setAddress(String address) {
        try {
            this.address = new URI("//" + address);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Can't parse " + address);
        }
        return this;
    }
    public URI getAddress() {
        return address;
    }
    void setAddress(URI address) {
        this.address = address;
    }

}
