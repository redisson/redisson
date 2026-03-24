/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.config;

import org.redisson.misc.RedisURI;

import java.util.Map;

/**
 * Maps host of RedisURI object using map defined in <code>hostsMap</code> setting.
 *
 * @author Nikita Koksharov
 *
 */
public class HostNatMapper implements NatMapper {

    private Map<String, String> hostsMap;

    @Override
    public RedisURI map(RedisURI uri) {
        String host = hostsMap.get(uri.getHost());
        if (host == null) {
            return uri;
        }
        return new RedisURI(uri.getScheme(), host, uri.getPort());
    }

    /**
     * Defines hosts mapping. Host as key mapped to host as value.
     *
     * @param hostsMap - hosts map
     */
    public void setHostsMap(Map<String, String> hostsMap) {
        this.hostsMap = hostsMap;
    }

}
