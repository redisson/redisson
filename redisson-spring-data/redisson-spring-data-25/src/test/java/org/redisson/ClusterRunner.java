package org.redisson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class ClusterRunner {
    
    private final LinkedHashMap<RedisRunner, String> nodes = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> slaveMasters = new LinkedHashMap<>();
    
    public ClusterRunner addNode(RedisRunner runner) {
        nodes.putIfAbsent(runner, getRandomId());
        if (!runner.hasOption(RedisRunner.REDIS_OPTIONS.CLUSTER_ENABLED)) {
            runner.clusterEnabled(true);
        }
        if (!runner.hasOption(RedisRunner.REDIS_OPTIONS.CLUSTER_NODE_TIMEOUT)) {
            runner.clusterNodeTimeout(5000);
        }
        if (!runner.hasOption(RedisRunner.REDIS_OPTIONS.PORT)) {
            runner.randomPort(1);
            runner.port(RedisRunner.findFreePort());
        }
        if (!runner.hasOption(RedisRunner.REDIS_OPTIONS.BIND)) {
            runner.bind("127.0.0.1");
        }
        return this;
    }
    
    public ClusterRunner addNode(RedisRunner master, RedisRunner... slaves) {
        addNode(master);
        for (RedisRunner slave : slaves) {
            addNode(slave);
            slaveMasters.put(nodes.get(slave), nodes.get(master));
        }
        return this;
    }
    
    public synchronized ClusterProcesses run() throws IOException, InterruptedException, RedisRunner.FailedToStartRedisException {
        BiHashMap<String, RedisRunner.RedisProcess> processes = new BiHashMap<>();
        for (RedisRunner runner : nodes.keySet()) {
            List<String> options = getClusterConfig(runner);
            String confFile = runner.dir() + File.separator + nodes.get(runner) + ".conf";
            System.out.println("WRITING CONFIG: for " + nodes.get(runner));
            try (PrintWriter printer = new PrintWriter(new FileWriter(confFile))) {
                options.stream().forEach((line) -> {
                    printer.println(line);
                    System.out.println(line);
                });
            }
            processes.put(nodes.get(runner), runner.clusterConfigFile(confFile).run());
        }
        Thread.sleep(1000);
        for (RedisRunner.RedisProcess process : processes.valueSet()) {
            if (!process.isAlive()) {
                throw new RedisRunner.FailedToStartRedisException();
            }
        }
        return new ClusterProcesses(processes);
    }
    
    private List<String> getClusterConfig(RedisRunner runner) {
        String me = runner.getInitialBindAddr() + ":" + runner.getPort();
        List<String> nodeConfig = new ArrayList<>();
        int c = 0;
        for (RedisRunner node : nodes.keySet()) {
            String nodeId = nodes.get(node);
            StringBuilder sb = new StringBuilder();
            String nodeAddr = node.getInitialBindAddr() + ":" + node.getPort();
            sb.append(nodeId).append(" ");
            sb.append(nodeAddr).append(" ");
            sb.append(me.equals(nodeAddr)
                    ? "myself,"
                    : "");
            boolean isMaster = !slaveMasters.containsKey(nodeId);
            if (isMaster) {
                 sb.append("master -");
            } else {
                sb.append("slave ").append(slaveMasters.get(nodeId));
            }
            sb.append(" ");
            sb.append("0").append(" ");
            sb.append(me.equals(nodeAddr)
                    ? "0"
                    : "1").append(" ");
            sb.append(c + 1).append(" ");
            sb.append("connected ");
            if (isMaster) {
                sb.append(getSlots(c, nodes.size() - slaveMasters.size()));
                c++;
            }
            nodeConfig.add(sb.toString());
        }
        nodeConfig.add("vars currentEpoch 0 lastVoteEpoch 0");
        return nodeConfig;
    }
    
    private static String getSlots(int index, int groupNum) {
        final double t = 16383;
        int start = index == 0 ? 0 : (int) (t / groupNum * index);
        int end = index == groupNum - 1 ? (int) t : (int) (t / groupNum * (index + 1)) - 1;
        return start + "-" + end;
    }

    private static String getRandomId() {
        final SecureRandom r = new SecureRandom();
        return new BigInteger(160, r).toString(16);
    }
    
    public static class ClusterProcesses {
        private final BiHashMap<String, RedisRunner.RedisProcess> processes;

        private ClusterProcesses(BiHashMap<String, RedisRunner.RedisProcess> processes) {
            this.processes = processes;
        }
        
        public RedisRunner.RedisProcess getProcess(String nodeId) {
            return processes.get(nodeId);
        }
        
        public String getNodeId(RedisRunner.RedisProcess process) {
            return processes.reverseGet(process);
        }
        
        public Set<RedisRunner.RedisProcess> getNodes() {
            return processes.valueSet();
        }
        
        public Set<String> getNodeIds() {
            return processes.keySet();
        }
        
        public synchronized Map<String, Integer> shutdown() {
            return processes
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey(),
                            e -> e.getValue().stop()));
        }
    }
}

class BiHashMap<K, V> implements Map<K, V> {

    private Map<K, V> keyValueMap = new HashMap<K, V>();
    private Map<V, K> valueKeyMap = new HashMap<V, K>();

    @Override
    public int size() {
        return keyValueMap.size();
    }

    @Override
    public boolean isEmpty() {
        return keyValueMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return keyValueMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return valueKeyMap.containsKey(value);
    }

    @Override
    public V get(Object key) {
        return keyValueMap.get(key);
    }

    public K reverseGet(Object key) {
        return valueKeyMap.get(key);
    }

    @Override
    public V put(K key, V value) {
        if (keyValueMap.containsKey(key)) {
            valueKeyMap.remove(keyValueMap.get(key));
        }
        valueKeyMap.put(value, key);
        return keyValueMap.put(key, value);
    }

    @Override
    public V remove(Object key) {
        V removed = keyValueMap.remove(key);
        if (removed != null) {
            valueKeyMap.remove(removed);
        }
        return removed;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        keyValueMap.clear();
        valueKeyMap.clear();
    }

    @Override
    public Set<K> keySet() {
        return keyValueMap.keySet();
    }

    public Set<V> valueSet() {
        return valueKeyMap.keySet();
    }

    @Override
    public Collection<V> values() {
        return keyValueMap.values();
    }

    public Collection<K> keys() {
        return valueKeyMap.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return keyValueMap.entrySet();
    }

    public Set<Entry<V, K>> reverseEntrySet() {
        return valueKeyMap.entrySet();
    }

    public void makeImmutable() {
        keyValueMap = Collections.unmodifiableMap(keyValueMap);
        valueKeyMap = Collections.unmodifiableMap(valueKeyMap);
    }
}
