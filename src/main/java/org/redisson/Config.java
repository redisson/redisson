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

import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.RedissonCodec;

/**
 * Redisson configuration
 *
 * @author Nikita Koksharov
 *
 */
public class Config {

    private MasterSlaveConnectionConfig masterSlaveConnectionConfig;

    private SingleConnectionConfig singleConnectionConfig;

    /**
     * Threads amount shared between all redis node clients
     */
    private int threads = 0; // 0 = current_processors_amount * 2

    /**
     * Redis key/value codec. JsonJacksonCodec used by default
     */
    private RedissonCodec codec;

    public Config() {
    }

    Config(Config oldConf) {
        if (oldConf.getCodec() == null) {
            // use it by default
            oldConf.setCodec(new JsonJacksonCodec());
        }

        setThreads(oldConf.getThreads());
        setCodec(oldConf.getCodec());
        if (oldConf.getSingleConnectionConfig() != null) {
            setSingleConnectionConfig(new SingleConnectionConfig(oldConf.getSingleConnectionConfig()));
        }
        if (oldConf.getMasterSlaveConnectionConfig() != null) {
            setMasterSlaveConnectionConfig(new MasterSlaveConnectionConfig(oldConf.getMasterSlaveConnectionConfig()));
        }
    }

    /**
     * Redis key/value codec. Default is json
     *
     * @see org.redisson.codec.JsonJacksonCodec
     * @see org.redisson.codec.SerializationCodec
     */
    public Config setCodec(RedissonCodec codec) {
        this.codec = codec;
        return this;
    }
    public RedissonCodec getCodec() {
        return codec;
    }

    public SingleConnectionConfig useSingleConnection() {
        if (masterSlaveConnectionConfig != null) {
            throw new IllegalStateException("master/slave connection already used!");
        }
        if (singleConnectionConfig == null) {
            singleConnectionConfig = new SingleConnectionConfig();
        }
        return singleConnectionConfig;
    }
    SingleConnectionConfig getSingleConnectionConfig() {
        return singleConnectionConfig;
    }
    void setSingleConnectionConfig(SingleConnectionConfig singleConnectionConfig) {
        this.singleConnectionConfig = singleConnectionConfig;
    }

    public MasterSlaveConnectionConfig useMasterSlaveConnection() {
        if (singleConnectionConfig != null) {
            throw new IllegalStateException("single connection already used!");
        }
        if (masterSlaveConnectionConfig == null) {
            masterSlaveConnectionConfig = new MasterSlaveConnectionConfig();
        }
        return masterSlaveConnectionConfig;
    }
    MasterSlaveConnectionConfig getMasterSlaveConnectionConfig() {
        return masterSlaveConnectionConfig;
    }
    void setMasterSlaveConnectionConfig(MasterSlaveConnectionConfig masterSlaveConnectionConfig) {
        this.masterSlaveConnectionConfig = masterSlaveConnectionConfig;
    }

    public int getThreads() {
        return threads;
    }

    public Config setThreads(int threads) {
        this.threads = threads;
        return this;
    }

}
