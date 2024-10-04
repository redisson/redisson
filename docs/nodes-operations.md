Redisson provides API to manage Redis or Valkey nodes. 

Code example of operations with **Cluster** setup:
```java
RedisCluster cluster = redisson.getRedisNodes(RedisNodes.CLUSTER);
cluster.pingAll();
Collection<RedisClusterMaster> masters = cluster.getMasters();
Collection<RedisClusterMaster> slaves = cluster.getSlaves();
```

Code example of operations with **Master Slave** setup:
```java
RedisMasterSlave masterSlave = redisson.getRedisNodes(RedisNodes.MASTER_SLAVE);
masterSlave.pingAll();
RedisMaster master = masterSlave.getMaster();
Collection<RedisSlave> slaves = masterSlave.getSlaves();
```

Code example of operations with **Sentinel** setup:
```java
RedisSentinelMasterSlave sentinelMasterSlave = redisson.getRedisNodes(RedisNodes.SENTINEL_MASTER_SLAVE);
sentinelMasterSlave.pingAll();
RedisMaster master = sentinelMasterSlave.getMaster();
Collection<RedisSlave> slaves = sentinelMasterSlave.getSlaves();
Collection<RedisSentinel> sentinels = sentinelMasterSlave.getSentinels();
```

Code example of operations with **Single** setup:
```java
RedisSingle single = redisson.getRedisNodes(RedisNodes.SINGLE);
single.pingAll();
RedisMaster instance = single.getInstance();
```
