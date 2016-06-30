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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;

import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;

import io.netty.channel.EventLoopGroup;

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

    private EventLoopGroup eventLoopGroup;

    public Config() {
    }

    public Config(Config oldConf) {
        setUseLinuxNativeEpoll(oldConf.isUseLinuxNativeEpoll());
        setEventLoopGroup(oldConf.getEventLoopGroup());

        if (oldConf.getCodec() == null) {
            // use it by default
            oldConf.setCodec(new JsonJacksonCodec());
        }

        setThreads(oldConf.getThreads());
        setCodec(oldConf.getCodec());
        setEventLoopGroup(oldConf.getEventLoopGroup());
        if (oldConf.getSingleServerConfig() != null) {
            setSingleServerConfig(new SingleServerConfig(oldConf.getSingleServerConfig()));
        }
        if (oldConf.getMasterSlaveServersConfig() != null) {
            setMasterSlaveServersConfig(new MasterSlaveServersConfig(oldConf.getMasterSlaveServersConfig()));
        }
        if (oldConf.getSentinelServersConfig() != null) {
            setSentinelServersConfig(new SentinelServersConfig(oldConf.getSentinelServersConfig()));
        }
        if (oldConf.getClusterServersConfig() != null) {
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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

    /**
     * Threads amount shared between all redis node clients.
     * <p/>
     * Default is <code>0</code>.
     * <p/>
     * <code>0</code> means <code>current_processors_amount * 2</code>
     *
     * @param threads
     * @return
     */
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

    /**
     * Use external EventLoopGroup. EventLoopGroup processes all
     * Netty connection tied with Redis servers. Each EventLoopGroup creates
     * own threads and each Redisson client creates own EventLoopGroup by default.
     * So if there are multiple Redisson instances in same JVM
     * it would be useful to share one EventLoopGroup among them.
     * <p/>
     * Only {@link io.netty.channel.epoll.EpollEventLoopGroup} or
     * {@link io.netty.channel.nio.NioEventLoopGroup} can be used.
     *
     * @param eventLoopGroup
     * @return
     */
    public Config setEventLoopGroup(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    public EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }

    /**
     * Read config object stored in JSON format from <code>String</code>
     *
     * @param content
     * @return
     * @throws IOException
     */
    public static Config fromJSON(String content) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(content);
    }

    /**
     * Read config object stored in JSON format from <code>InputStream</code>
     *
     * @param inputStream
     * @return
     * @throws IOException
     */
    public static Config fromJSON(InputStream inputStream) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(inputStream);
    }

    /**
     * Read config object stored in JSON format from <code>File</code>
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static Config fromJSON(File file) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(file);
    }

    /**
     * Read config object stored in JSON format from <code>URL</code>
     *
     * @param url
     * @return
     * @throws IOException
     */
    public static Config fromJSON(URL url) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(url);
    }

    /**
     * Read config object stored in JSON format from <code>Reader</code>
     *
     * @param reader
     * @return
     * @throws IOException
     */
    public static Config fromJSON(Reader reader) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(reader);
    }

    /**
     * Convert current configuration to JSON format
     *
     * @return
     * @throws IOException
     */
    public String toJSON() throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.toJSON(this);
    }

    /**
     * Read config object stored in YAML format from <code>String</code>
     *
     * @param content
     * @return
     * @throws IOException
     */
    public static Config fromYAML(String content) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(content);
    }

    /**
     * Read config object stored in YAML format from <code>InputStream</code>
     *
     * @param inputStream
     * @return
     * @throws IOException
     */
    public static Config fromYAML(InputStream inputStream) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(inputStream);
    }

    /**
     * Read config object stored in YAML format from <code>File</code>
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static Config fromYAML(File file) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(file);
    }

    /**
     * Read config object stored in YAML format from <code>URL</code>
     *
     * @param url
     * @return
     * @throws IOException
     */
    public static Config fromYAML(URL url) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(url);
    }

    /**
     * Read config object stored in YAML format from <code>Reader</code>
     *
     * @param reader
     * @return
     * @throws IOException
     */
    public static Config fromYAML(Reader reader) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(reader);
    }

    /**
     * Convert current configuration to YAML format
     *
     * @return
     * @throws IOException
     */
    public String toYAML() throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.toYAML(this);
    }

}
