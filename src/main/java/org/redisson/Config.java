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
import org.redisson.connection.LoadBalancer;
import org.redisson.connection.RoundRobinLoadBalancer;

/**
 * Redisson configuration
 *
 * @author Nikita Koksharov
 *
 */
public class Config {

    /**
     * Сonnection load balancer to use multiple Redis servers
     */
    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

    /**
     * Redis key/value codec
     */
    private RedissonCodec codec = new JsonJacksonCodec();

    /**
     * Subscriptions per Redis connection limit
     */
    private int subscriptionsPerConnection = 5;

    /**
     * Redis connection pool size limit
     */
    private int connectionPoolSize = 100;

    /**
     * Password for Redis authentication. Should be null if not needed
     */
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
        setLoadBalancer(oldConf.getLoadBalancer());
    }

    /**
     * Redis key/value codec. Default is json
     *
     * @see org.redisson.codec.JsonJacksonCodec
     * @see org.redisson.codec.SerializationCodec
     */
    public void setCodec(RedissonCodec codec) {
        this.codec = codec;
    }
    public RedissonCodec getCodec() {
        return codec;
    }

    /**
     * Password for Redis authentication. Should be null if not needed
     * Default is <code>null</code>
     *
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }
    public String getPassword() {
        return password;
    }

    /**
     * Subscriptions per Redis connection limit
     * Default is 5
     *
     * @param subscriptionsPerConnection
     */
    public void setSubscriptionsPerConnection(int subscriptionsPerConnection) {
        this.subscriptionsPerConnection = subscriptionsPerConnection;
    }
    public int getSubscriptionsPerConnection() {
        return subscriptionsPerConnection;
    }

    /**
     * Redis connection pool size limit
     * Default is 100
     *
     * @param connectionPoolSize
     */
    public void setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }
    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    /**
     * Redis server address. Use follow format -- host:port
     *
     * @param addressesVar
     */
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

    /**
     * Сonnection load balancer to multiple Redis servers.
     * Uses Round-robin algorithm by default
     *
     * @param loadBalancer
     *
     * @see org.redisson.connection.RoundRobinLoadBalancer
     * @see org.redisson.connection.RandomLoadBalancer
     */
    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

}
