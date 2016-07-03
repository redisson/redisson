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
package org.redisson;

class BaseConfig<T extends BaseConfig<T>> {

    /**
     * If pooled connection not used for a <code>timeout</code> time
     * and current connections amount bigger than minimum idle connections pool size,
     * then it will closed and removed from pool.
     * Value in milliseconds.
     *
     */
    private int idleConnectionTimeout = 10000;

    /**
     * Ping timeout used in <code>Node.ping</code> and <code>Node.pingAll<code> operation.
     * Value in milliseconds.
     *
     */
    private int pingTimeout = 1000;

    /**
     * Timeout during connecting to any Redis server.
     * Value in milliseconds.
     *
     */
    private int connectTimeout = 1000;

    /**
     * Redis server response timeout. Starts to countdown when Redis command was succesfully sent.
     * Value in milliseconds.
     *
     */
    private int timeout = 1000;

    private int retryAttempts = 3;

    private int retryInterval = 1000;

    /**
     * Reconnection attempt timeout to Redis server then
     * it has been excluded from internal list of available servers.
     *
     * On every such timeout event Redisson tries
     * to connect to disconnected Redis server.
     *
     * @see #failedAttempts
     *
     */
    private int reconnectionTimeout = 3000;

    /**
     * Redis server will be excluded from the list of available nodes
     * when sequential unsuccessful execution attempts of any Redis command
     * reaches <code>failedAttempts</code>.
     */
    private int failedAttempts = 3;

    /**
     * Password for Redis authentication. Should be null if not needed
     */
    private String password;

    /**
     * Subscriptions per Redis connection limit
     */
    private int subscriptionsPerConnection = 5;

    /**
     * Name of client connection
     */
    private String clientName;

    BaseConfig() {
    }

    BaseConfig(T config) {
        setPassword(config.getPassword());
        setSubscriptionsPerConnection(config.getSubscriptionsPerConnection());
        setRetryAttempts(config.getRetryAttempts());
        setRetryInterval(config.getRetryInterval());
        setTimeout(config.getTimeout());
        setClientName(config.getClientName());
        setPingTimeout(config.getPingTimeout());
        setConnectTimeout(config.getConnectTimeout());
        setIdleConnectionTimeout(config.getIdleConnectionTimeout());
        setFailedAttempts(config.getFailedAttempts());
        setReconnectionTimeout(config.getReconnectionTimeout());
    }

    /**
     * Subscriptions per Redis connection limit
     * Default is 5
     *
     * @param subscriptionsPerConnection
     */
    public T setSubscriptionsPerConnection(int subscriptionsPerConnection) {
        this.subscriptionsPerConnection = subscriptionsPerConnection;
        return (T) this;
    }

    public int getSubscriptionsPerConnection() {
        return subscriptionsPerConnection;
    }

    /**
     * Password for Redis authentication. Should be null if not needed
     * Default is <code>null</code>
     *
     * @param password
     */
    public T setPassword(String password) {
        this.password = password;
        return (T) this;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Error will be thrown if Redis command can't be sended to Redis server after <code>retryAttempts</code>.
     * But if it sent succesfully then <code>timeout</code> will be started.
     *
     * @see #timeout
     * @param retryAttempts
     */
    public T setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
        return (T) this;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    /**
     * Time interval after which another one attempt to send Redis command will be executed.
     *
     * @see retryAttempts
     * @param retryInterval - time in milliseconds
     */
    public T setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
        return (T) this;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    /**
     * Redis server response timeout.
     *
     * @param timeout in milliseconds
     */
    public T setTimeout(int timeout) {
        this.timeout = timeout;
        return (T) this;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Setup connection name during connection init
     * via CLIENT SETNAME command
     *
     * @param name
     */
    public T setClientName(String clientName) {
        this.clientName = clientName;
        return (T) this;
    }

    public String getClientName() {
        return clientName;
    }

    /**
     * Ping timeout used in <code>Node.ping</code> and <code>Node.pingAll<code> operation
     *
     * @param ping timeout in milliseconds
     */
    public T setPingTimeout(int pingTimeout) {
        this.pingTimeout = pingTimeout;
        return (T) this;
    }

    public int getPingTimeout() {
        return pingTimeout;
    }

    /**
     * Timeout during connecting to any Redis server.
     * <p/>
     * @param connectTimeout - timeout in milliseconds
     * @return
     */
    public T setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return (T) this;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * If pooled connection not used for a <code>timeout</code> time
     * and current connections amount bigger than minimum idle connections pool size,
     * then it will closed and removed from pool.
     *
     * @param idleConnectionTimeout - timeout in milliseconds
     * @return
     */
    public T setIdleConnectionTimeout(int idleConnectionTimeout) {
        this.idleConnectionTimeout = idleConnectionTimeout;
        return (T) this;
    }

    public int getIdleConnectionTimeout() {
        return idleConnectionTimeout;
    }

    /**
     * Reconnection attempt timeout to Redis server when
     * it has been excluded from internal list of available servers.
     * <p/>
     * On every such timeout event Redisson tries
     * to connect to disconnected Redis server.
     * <p/>
     * Default is 3000
     *
     * @see #failedAttempts
     *
     */

    public T setReconnectionTimeout(int slaveRetryTimeout) {
        this.reconnectionTimeout = slaveRetryTimeout;
        return (T) this;
    }

    public int getReconnectionTimeout() {
        return reconnectionTimeout;
    }

    /**
     * Redis server will be excluded from the internal list of available nodes
     * when sequential unsuccessful execution attempts of any Redis command
     * on this server reaches <code>failedAttempts</code>.
     * <p/>
     * Default is 3
     *
     */
    public T setFailedAttempts(int slaveFailedAttempts) {
        this.failedAttempts = slaveFailedAttempts;
        return (T) this;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

}
