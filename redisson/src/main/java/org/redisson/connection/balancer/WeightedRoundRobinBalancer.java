/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.connection.balancer;

import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.misc.RedisURI;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Weighted Round Robin balancer.
 *
 * @author Nikita Koksharov
 *
 */
public class WeightedRoundRobinBalancer implements LoadBalancer {

    static class WeightEntry {

        final int weight;
        int weightCounter;

        WeightEntry(int weight) {
            this.weight = weight;
            this.weightCounter = weight;
        }

        public boolean isWeightCounterZero() {
            return weightCounter == 0;
        }

        public void decWeightCounter() {
            weightCounter--;
        }

        public void resetWeightCounter() {
            weightCounter = weight;
        }

    }

    private final AtomicInteger index = new AtomicInteger(-1);

    private final Map<InetSocketAddress, WeightEntry> weights = new ConcurrentHashMap<>();

    private final int defaultWeight;

    /**
     * Creates weighted round robin balancer.
     *
     * @param weights - weight mapped by slave node addr in <code>redis://host:port</code> format
     * @param defaultWeight - default weight value assigns to slaves not defined in weights map
     */
    public WeightedRoundRobinBalancer(Map<String, Integer> weights, int defaultWeight) {
        for (Entry<String, Integer> entry : weights.entrySet()) {
            RedisURI uri = new RedisURI(entry.getKey());
            InetSocketAddress addr = new InetSocketAddress(uri.getHost(), uri.getPort());
            if (entry.getValue() <= 0) {
                throw new IllegalArgumentException("Weight can't be less than or equal zero");
            }
            this.weights.put(addr, new WeightEntry(entry.getValue()));
        }
        if (defaultWeight <= 0) {
            throw new IllegalArgumentException("Weight can't be less than or equal zero");
        }

        this.defaultWeight = defaultWeight;
    }

    @Override
    public ClientConnectionsEntry getEntry(List<ClientConnectionsEntry> clients) {
        clients.stream()
                .map(e -> e.getClient().getAddr())
                .distinct()
                .filter(a -> !weights.containsKey(a))
                .forEach(a -> weights.put(a, new WeightEntry(defaultWeight)));

        Map<InetSocketAddress, WeightEntry> weightsCopy = new HashMap<>(weights);

        synchronized (this) {
            weightsCopy.values().removeIf(WeightEntry::isWeightCounterZero);

            if (weightsCopy.isEmpty()) {
                for (WeightEntry entry : weights.values()) {
                    entry.resetWeightCounter();
                }

                weightsCopy = weights;
            }

            List<ClientConnectionsEntry> clientsCopy = findClients(clients, weightsCopy);

            // If there are no connections available to servers that have a weight counter
            // remaining, then reset the weight counters and find a connection again. In the worst
            // case, there should always be a connection to the master.
            if (clientsCopy.isEmpty()) {
                for (WeightEntry entry : weights.values()) {
                    entry.resetWeightCounter();
                }

                weightsCopy = weights;
                clientsCopy = findClients(clients, weightsCopy);
            }

            int ind = Math.abs(index.incrementAndGet() % clientsCopy.size());
            ClientConnectionsEntry entry = clientsCopy.get(ind);
            WeightEntry weightEntry = weights.get(entry.getClient().getAddr());
            weightEntry.decWeightCounter();
            return entry;
        }
    }

    private List<ClientConnectionsEntry> findClients(List<ClientConnectionsEntry> clients,
                                                        Map<InetSocketAddress, WeightEntry> weightsCopy) {
        return clients.stream()
                        .filter(e -> weightsCopy.containsKey(e.getClient().getAddr()))
                        .collect(Collectors.toList());
    }

}
