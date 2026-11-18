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
package org.redisson.helidon;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.PropertiesConvertor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Helidon service registry factory of {@link RedissonClient} instances.
 * <p>
 * Each child of the {@code org.redisson.Redisson} config node defines a Redisson instance
 * qualified with {@link Service.Named} by the child name. Instance named {@code default}
 * is used for injection points without qualifier. Instances are created on first use
 * and shut down with the service registry.
 *
 * @author Nikita Koksharov
 *
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class RedissonFactory implements Service.ServicesFactory<RedissonClient> {

    private static final String DEFAULT_INSTANCE = "default";

    private final Supplier<Config> config;
    private final List<LazyValue<RedissonClient>> clients = new CopyOnWriteArrayList<>();

    @Service.Inject
    RedissonFactory(Supplier<Config> config) {
        this.config = config;
    }

    @Override
    public List<Service.QualifiedInstance<RedissonClient>> services() {
        // config nodes are unordered, sort them to keep the instance order stable
        List<Config> instances = config.get().get(Redisson.class.getName())
                .asNodeList()
                .orElseGet(List::of)
                .stream()
                .sorted(Comparator.comparing(Config::name))
                .toList();

        List<Service.QualifiedInstance<RedissonClient>> result = new ArrayList<>();
        for (Config instance : instances) {
            LazyValue<RedissonClient> client = LazyValue.create(() -> createClient(instance));
            clients.add(client);

            RedissonInstance qualifiedInstance = new RedissonInstance(client, Set.of(Qualifier.createNamed(instance.name())));
            if (DEFAULT_INSTANCE.equals(instance.name())) {
                // the first matching instance is injected into injection points without qualifier
                result.addFirst(qualifiedInstance);
            } else {
                result.add(qualifiedInstance);
            }
        }
        return result;
    }

    @Service.PreDestroy
    void shutdown() {
        for (LazyValue<RedissonClient> client : clients) {
            if (client.isLoaded()) {
                client.get().shutdown();
            }
        }
    }

    static org.redisson.config.Config toRedissonConfig(Config instance) {
        Map<String, String> properties = new HashMap<>();
        collectProperties(instance, properties);

        String yamlConfig = PropertiesConvertor.toYaml(instance.key() + ".", properties.keySet(), properties::get, true);
        return org.redisson.config.Config.fromYAML(yamlConfig);
    }

    private static void collectProperties(Config node, Map<String, String> properties) {
        switch (node.type()) {
            case OBJECT -> node.asNodeList().orElseGet(List::of).forEach(child -> collectProperties(child, properties));
            case LIST -> properties.put(node.key().toString(), String.join(",", node.asList(String.class).get()));
            case VALUE -> properties.put(node.key().toString(), node.asString().get());
            default -> {
            }
        }
    }

    private static RedissonClient createClient(Config instance) {
        try {
            return Redisson.create(toRedissonConfig(instance));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Redisson instance '" + instance.name() + "'", e);
        }
    }

    private record RedissonInstance(LazyValue<RedissonClient> client, Set<Qualifier> qualifiers)
            implements Service.QualifiedInstance<RedissonClient> {

        @Override
        public RedissonClient get() {
            return client.get();
        }

    }

}
