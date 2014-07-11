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

public class SentinelServersConfig extends BaseMasterSlaveServersConfig<SentinelServersConfig> {

    private List<URI> sentinelAddresses = new ArrayList<URI>();

    private String masterName;

    public SentinelServersConfig() {
    }

    SentinelServersConfig(SentinelServersConfig config) {
        super(config);
        setSentinelAddresses(config.getSentinelAddresses());
        setMasterName(config.getMasterName());
    }

    public SentinelServersConfig setMasterName(String masterName) {
        this.masterName = masterName;
        return this;
    }
    public String getMasterName() {
        return masterName;
    }

    public SentinelServersConfig addSentinelAddress(String ... addresses) {
        for (String address : addresses) {
            try {
                sentinelAddresses.add(new URI("//" + address));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Can't parse " + address);
            }
        }
        return this;
    }
    public List<URI> getSentinelAddresses() {
        return sentinelAddresses;
    }
    void setSentinelAddresses(List<URI> sentinelAddresses) {
        this.sentinelAddresses = sentinelAddresses;
    }

}
