/**
 * Copyright (c) 2013-2024 Nikita Koksharov
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.spring.starter;

import org.redisson.misc.RedisURI;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapts {@link RedisProperties} to {@link RedisConnectionDetails}.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Scott Frederick
 * @author Nikita Koksharov
 */
public class PropertiesRedisConnectionDetails implements RedisConnectionDetails {

    private final RedisProperties properties;

    PropertiesRedisConnectionDetails(RedisProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getUsername() {
        if (this.properties.getUrl() != null) {
            RedisURI uri = parseURL();
            return uri.getUsername();
        }
        return this.properties.getUsername();
    }

    @Override
    public String getPassword() {
        if (this.properties.getUrl() != null) {
            RedisURI uri = parseURL();
            return uri.getPassword();
        }
        return this.properties.getPassword();
    }

    @Override
    public Standalone getStandalone() {
        if (this.properties.getUrl() != null) {
            RedisURI uri = parseURL();
            return Standalone.of(uri.getHost(), uri.getPort(),
                    this.properties.getDatabase());
        }
        return Standalone.of(this.properties.getHost(), this.properties.getPort(), this.properties.getDatabase());
    }

    private RedisURI parseURL() {
        if (this.properties.getUrl() != null) {
            return new RedisURI(this.properties.getUrl());
        }
        return null;
    }

    @Override
    public Sentinel getSentinel() {
        RedisProperties.Sentinel sentinel = this.properties
                .getSentinel();
        if (sentinel == null) {
            return null;
        }
        return new Sentinel() {

            @Override
            public int getDatabase() {
                return PropertiesRedisConnectionDetails.this.properties.getDatabase();
            }

            @Override
            public String getMaster() {
                return sentinel.getMaster();
            }

            @Override
            public List<Node> getNodes() {
                return sentinel.getNodes().stream().map(PropertiesRedisConnectionDetails.this::asNode).collect(Collectors.toList());
            }

            @Override
            public String getUsername() {
                return sentinel.getUsername();
            }

            @Override
            public String getPassword() {
                return sentinel.getPassword();
            }

        };
    }

    @Override
    public Cluster getCluster() {
        RedisProperties.Cluster cluster = this.properties.getCluster();
        if (cluster != null) {
            return () -> cluster.getNodes().stream()
                                           .map(this::asNode)
                                           .collect(Collectors.toList());
        }
        return null;
    }

    private Node asNode(String node) {
        String[] components = node.split(":");
        return new Node(components[0], Integer.parseInt(components[1]));
    }

}
