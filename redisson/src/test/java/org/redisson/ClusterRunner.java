package org.redisson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class ClusterRunner {
    
    private final LinkedHashMap<RedisRunner, String> nodes = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> masters = new LinkedHashMap<>();
    
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
            masters.put(nodes.get(slave), nodes.get(master));
        }
        return this;
    }
    
    public List<RedisRunner.RedisProcess> run() throws IOException, InterruptedException, RedisRunner.FailedToStartRedisException {
        ArrayList<RedisRunner.RedisProcess> processes = new ArrayList<>();
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
            processes.add(runner.clusterConfigFile(confFile).run());
        }
        Thread.sleep(1000);
        for (RedisRunner.RedisProcess process : processes) {
            if (!process.isAlive()) {
                throw new RedisRunner.FailedToStartRedisException();
            }
        }
        return processes;
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
            if (!masters.containsKey(nodeId)) {
                 sb.append("master -");
            } else {
                sb.append("slave ").append(masters.get(nodeId));
            }
            sb.append(" ");
            sb.append("0").append(" ");
            sb.append(me.equals(nodeAddr)
                    ? "0"
                    : "1").append(" ");
            sb.append(c + 1).append(" ");
            sb.append("connected ");
            sb.append(getSlots(c, nodes.size()));
            c++;
            nodeConfig.add(sb.toString());
        }
        nodeConfig.add("vars currentEpoch 0 lastVoteEpoch 0");
        return nodeConfig;
    }
    
    private String getSlots(int index, int groupNum) {
        final double t = 16383;
        int start = index == 0 ? 0 : (int) (t / groupNum * index);
        int end = index == groupNum - 1 ? (int) t : (int) (t / groupNum * (index + 1)) - 1;
        return start + "-" + end;
    }

    private String getRandomId() {
        final SecureRandom r = new SecureRandom();
        return new BigInteger(160, r).toString(16);
    }
}
