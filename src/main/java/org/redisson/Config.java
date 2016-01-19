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

import java.io.IOException;

import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;

/**
 * Redisson configuration
 *
 * @author Nikita Koksharov
 *
 */
public class Config {

    private SentinelServersConfig sentinelServersConfig;

    private MasterSlaveServersConfig masterSlaveServersConfig;

    private SingleServerConfig singleServerConfig;

    private ClusterServersConfig clusterServersConfig;

    private ElasticacheServersConfig elasticacheServersConfig;

    /**
     * Threads amount shared between all redis node clients
     */
    private int threads = 0; // 0 = current_processors_amount * 2

    /**
     * Redis key/value codec. JsonJacksonCodec used by default
     */
    private Codec codec;

    private boolean useLinuxNativeEpoll;

    public Config() {
    }

    public Config(Config oldConf) {
        setUseLinuxNativeEpoll(oldConf.isUseLinuxNativeEpoll());

        if (oldConf.getCodec() == null) {
            // use it by default
            oldConf.setCodec(new JsonJacksonCodec());
        }

        setThreads(oldConf.getThreads());
        setCodec(oldConf.getCodec());
        if (oldConf.getSingleServerConfig() != null) {
            setSingleServerConfig(new SingleServerConfig(oldConf.getSingleServerConfig()));
        }
        if (oldConf.getMasterSlaveServersConfig() != null) {
            setMasterSlaveServersConfig(new MasterSlaveServersConfig(oldConf.getMasterSlaveServersConfig()));
        }
        if (oldConf.getSentinelServersConfig() != null ) {
            setSentinelServersConfig(new SentinelServersConfig(oldConf.getSentinelServersConfig()));
        }
        if (oldConf.getClusterServersConfig() != null ) {
            setClusterServersConfig(new ClusterServersConfig(oldConf.getClusterServersConfig()));
        }
        if (oldConf.getElasticacheServersConfig() != null) {
            setElasticacheServersConfig(new ElasticacheServersConfig(oldConf.getElasticacheServersConfig()));
        }

    }

    /**
     * Redis key/value codec. Default is json-codec
     *
     * @see org.redisson.client.codec.Codec
     */
    public Config setCodec(Codec codec) {
        this.codec = codec;
        return this;
    }
    public Codec getCodec() {
        return codec;
    }

    /**
     * Init cluster servers configuration
     *
     * @return
     */
    public ClusterServersConfig useClusterServers() {
        return useClusterServers(new ClusterServersConfig());
    }

    /**
     * Init cluster servers configuration by config object.
     *
     * @return
     */
    public ClusterServersConfig useClusterServers(ClusterServersConfig config) {
        checkMasterSlaveServersConfig();
        checkSentinelServersConfig();
        checkSingleServerConfig();
        checkElasticacheServersConfig();

        if (clusterServersConfig == null) {
            clusterServersConfig = config;
        }
        return clusterServersConfig;
    }


    ClusterServersConfig getClusterServersConfig() {
        return clusterServersConfig;
    }
    void setClusterServersConfig(ClusterServersConfig clusterServersConfig) {
        this.clusterServersConfig = clusterServersConfig;
    }

    /**
     * Init AWS Elasticache servers configuration.
     *
     * @return
     */
    public ElasticacheServersConfig useElasticacheServers() {
        return useElasticacheServers(new ElasticacheServersConfig());
    }

    /**
     * Init AWS Elasticache servers configuration by config object.
     *
     * @return
     */
    public ElasticacheServersConfig useElasticacheServers(ElasticacheServersConfig config) {
        checkClusterServersConfig();
        checkMasterSlaveServersConfig();
        checkSentinelServersConfig();
        checkSingleServerConfig();

        if (elasticacheServersConfig == null) {
            elasticacheServersConfig = new ElasticacheServersConfig();
        }
        return elasticacheServersConfig;
    }

    ElasticacheServersConfig getElasticacheServersConfig() {
        return elasticacheServersConfig;
    }
    void setElasticacheServersConfig(ElasticacheServersConfig elasticacheServersConfig) {
        this.elasticacheServersConfig = elasticacheServersConfig;
    }

    /**
     * Init single server configuration.
     *
     * @return
     */
    public SingleServerConfig useSingleServer() {
        return useSingleServer(new SingleServerConfig());
    }

    /**
     * Init single server configuration by config object.
     *
     * @return
     */
    public SingleServerConfig useSingleServer(SingleServerConfig config) {
        checkClusterServersConfig();
        checkMasterSlaveServersConfig();
        checkSentinelServersConfig();
        checkElasticacheServersConfig();

        if (singleServerConfig == null) {
            singleServerConfig = config;
        }
        return singleServerConfig;
    }


    SingleServerConfig getSingleServerConfig() {
        return singleServerConfig;
    }
    void setSingleServerConfig(SingleServerConfig singleConnectionConfig) {
        this.singleServerConfig = singleConnectionConfig;
    }

    /**
     * Init sentinel servers configuration.
     *
     * @return
     */
    public SentinelServersConfig useSentinelServers() {
        return useSentinelServers(new SentinelServersConfig());
    }

    /**
     * Init sentinel servers configuration by config object.
     *
     * @return
     */
    public SentinelServersConfig useSentinelServers(SentinelServersConfig sentinelServersConfig) {
        checkClusterServersConfig();
        checkSingleServerConfig();
        checkMasterSlaveServersConfig();
        checkElasticacheServersConfig();

        if (this.sentinelServersConfig == null) {
            this.sentinelServersConfig = sentinelServersConfig;
        }
        return this.sentinelServersConfig;
    }

    /**
     * Deprecated. Use {@link #useSentinelServers()} instead
     */
    @Deprecated
    public SentinelServersConfig useSentinelConnection() {
        return useSentinelServers();
    }

    SentinelServersConfig getSentinelServersConfig() {
        return sentinelServersConfig;
    }
    void setSentinelServersConfig(SentinelServersConfig sentinelConnectionConfig) {
        this.sentinelServersConfig = sentinelConnectionConfig;
    }

    /**
     * Init master/slave servers configuration.
     *
     * @return
     */
    public MasterSlaveServersConfig useMasterSlaveServers() {
        return useMasterSlaveServers(new MasterSlaveServersConfig());
    }

    /**
     * Init master/slave servers configuration by config object.
     *
     * @return
     */
    public MasterSlaveServersConfig useMasterSlaveServers(MasterSlaveServersConfig config) {
        checkClusterServersConfig();
        checkSingleServerConfig();
        checkSentinelServersConfig();
        checkElasticacheServersConfig();

        if (masterSlaveServersConfig == null) {
            masterSlaveServersConfig = config;
        }
        return masterSlaveServersConfig;
    }

    /**
     * Deprecated. Use {@link #useMasterSlaveServers()} instead
     */
    @Deprecated
    public MasterSlaveServersConfig useMasterSlaveConnection() {
        return useMasterSlaveServers();
    }
    MasterSlaveServersConfig getMasterSlaveServersConfig() {
        return masterSlaveServersConfig;
    }
    void setMasterSlaveServersConfig(MasterSlaveServersConfig masterSlaveConnectionConfig) {
        this.masterSlaveServersConfig = masterSlaveConnectionConfig;
    }

    public boolean isClusterConfig() {
        return clusterServersConfig != null;
    }

    public int getThreads() {
        return threads;
    }

    public Config setThreads(int threads) {
        this.threads = threads;
        return this;
    }

    private void checkClusterServersConfig() {
        if (clusterServersConfig != null) {
            throw new IllegalStateException("cluster servers config already used!");
        }
    }

    private void checkSentinelServersConfig() {
        if (sentinelServersConfig != null) {
            throw new IllegalStateException("sentinel servers config already used!");
        }
    }

    private void checkMasterSlaveServersConfig() {
        if (masterSlaveServersConfig != null) {
            throw new IllegalStateException("master/slave servers already used!");
        }
    }

    private void checkSingleServerConfig() {
        if (singleServerConfig != null) {
            throw new IllegalStateException("single server config already used!");
        }
    }

    private void checkElasticacheServersConfig() {
        if (elasticacheServersConfig != null) {
            throw new IllegalStateException("elasticache replication group servers config already used!");
        }
    }

    /**
     * Activates an unix socket if servers binded to loopback interface.
     * Also used for epoll transport activation.
     * <b>netty-transport-native-epoll</b> library should be in classpath
     *
     * @param useLinuxNativeEpoll
     * @return
     */
    public Config setUseLinuxNativeEpoll(boolean useLinuxNativeEpoll) {
        this.useLinuxNativeEpoll = useLinuxNativeEpoll;
        return this;
    }
    public boolean isUseLinuxNativeEpoll() {
        return useLinuxNativeEpoll;
    }

    public static Config fromJSON(String content) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(content);
    }

    public String toJSON() throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.toJSON(this);
    }


}
