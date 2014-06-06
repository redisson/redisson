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

public class SingleConnectionConfig extends BaseConfig<SingleConnectionConfig> {

    /**
     * Redis server address
     *
     */
    private URI address;

    /**
     * Redis connection pool size limit
     */
    private int connectionPoolSize = 100;

    SingleConnectionConfig() {
    }

    public SingleConnectionConfig(SingleConnectionConfig config) {
        super(config);
        setAddress(config.getAddress());
        setConnectionPoolSize(config.getConnectionPoolSize());
    }

    /**
     * Redis connection pool size limit
     * Default is 100
     *
     * @param connectionPoolSize
     */
    public SingleConnectionConfig setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
        return this;
    }
    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    /**
     * Set server address. Use follow format -- host:port
     *
     * @param address
     */
    public SingleConnectionConfig setAddress(String address) {
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
    public void setAddress(URI address) {
        this.address = address;
    }

}
