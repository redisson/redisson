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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.redisson.misc.URIBuilder;

public class SentinelServersConfig extends BaseMasterSlaveServersConfig<SentinelServersConfig> {

    private List<URI> sentinelAddresses = new ArrayList<URI>();

    private String masterName;

    /**
     * Database index used for Redis connection
     */
    private int database = 0;

    public SentinelServersConfig() {
    }

    SentinelServersConfig(SentinelServersConfig config) {
        super(config);
        setSentinelAddresses(config.getSentinelAddresses());
        setMasterName(config.getMasterName());
        setDatabase(config.getDatabase());
    }

    /**
     * Master server name used by Redis Sentinel servers and master change monitoring task.
     *
     * @param masterName
     * @return
     */
    public SentinelServersConfig setMasterName(String masterName) {
        this.masterName = masterName;
        return this;
    }
    public String getMasterName() {
        return masterName;
    }

    /**
     * Add Redis Sentinel node address in host:port format. Multiple nodes at once could be added.
     *
     * @param addresses
     * @return
     */
    public SentinelServersConfig addSentinelAddress(String ... addresses) {
        for (String address : addresses) {
            sentinelAddresses.add(URIBuilder.create(address));
        }
        return this;
    }
    public List<URI> getSentinelAddresses() {
        return sentinelAddresses;
    }
    void setSentinelAddresses(List<URI> sentinelAddresses) {
        this.sentinelAddresses = sentinelAddresses;
    }

    /**
     * Database index used for Redis connection
     * Default is <code>0</code>
     *
     * @param database
     */
    public SentinelServersConfig setDatabase(int database) {
        this.database = database;
        return this;
    }
    public int getDatabase() {
        return database;
    }

}
