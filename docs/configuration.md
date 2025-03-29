## Using Redisson API
Programmatic configuration is performed by the `Config` object instance. For example:
```java
Config config = new Config();
config.setTransportMode(TransportMode.EPOLL);
config.useClusterServers()
      // use "redis://" for Redis connection
      // use "valkey://" for Valkey connection
      // use "valkeys://" for Valkey SSL connection
      // use "rediss://" for Redis SSL connection
      .addNodeAddress("redis://127.0.0.1:7181");

RedissonClient redisson = Redisson.create(config);
```
## Using YAML
Redisson can also be configured in a declarative way by using a user-supplied text file in YAML format.

### YAML config
Redisson configuration could be stored in YAML format.
Use the `config.fromYAML()` method to read the configuration stored in YAML format:
```java
Config config = Config.fromYAML(new File("config-file.yaml"));  
RedissonClient redisson = Redisson.create(config);
```
Use the `config.toYAML()` method to write a configuration in YAML format:
```java
Config config = new Config();
// ... many settings are set here
String yamlFormat = config.toYAML();
```
### Variable syntax

Variables are used to parameterize the Redisson configuration and are then substituted with the actual values upon Redisson startup. Variables are defined using the `${variable_name}` syntax. To resolve variable references to their values, the following sources are consulted in sequence of precedence, with later sources preceding earlier ones:

* environment variables
* Java system properties

Definition example:
```yaml
singleServerConfig:
  address: "redis://127.0.0.1:${REDIS_PORT}"
```

Default values complies with shell format. Example:
```yaml
singleServerConfig:
  address: "redis://127.0.0.1:${REDIS_PORT:-6379}"
```

### Passwords encryption

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._  

Redisson supports Advanced Encryption Standard (AES) encryption for passwords defined in the configuration file, with the secret key stored in the file. The encryption key is derived from `PBKDF2WithHmacSHA512` and the scheme used is `AES/GCM/NoPadding`.

The `org.redisson.config.PasswordCipher` class is used to encrypt passwords. The secret key file may contain any characters. The encrypted password has the `{aes}` prefix.

Syntax:
```
java -cp redisson-all.jar org.redisson.config.PasswordCipher encode <unencrypted password> <path to secret key file>
```
Usage example:
```
java -cp redisson-all.jar org.redisson.config.PasswordCipher encode pass123 secret_key.txt
```
Output:
```
{aes}AuWmZDUXBTHBSaBXjqgsL4rXF+c2XcCmXwBr/pyLy9K651I0syX7FFkLEkuq1/rJHvZAyeeEIw==
```

The secret key file is defined through the `secretKey` setting in the Redisson configuration YAML file and applied to all encrypted passwords.

Configuration YAML file example:

```yaml
singleServerConfig:
   address: "rediss://127.0.0.1:6379"
   password: "{aes}h8/9bGMTf809PxsBL4JlKAbFffaMtcr1/SFdXBcWySaxETKylJziUM23oWxGAmSZHkm+y/yTRg=="
   sslTruststore: file:truststore
   sslTruststorePassword: "{aes}djXKclV2zFMc/tZdnntaTx2bRD3eJ1vtJSJFcBfp/9ZPzsnUw5f7zZXzwbbg2jPCr24TiJb7bQ=="
   secretKey: file:secret_key
```
## Common settings

The following settings belong to the `org.redisson.Config` object and are common for all modes:

**codec**

Default value: `org.redisson.codec.Kryo5Codec`  

Redis or Valkey data codec. Used during read/write data operations. Several implementations are [available](data-and-services/data-serialization.md).

**connectionListener**

Default value: `null`

The connection listener, which is triggered when Redisson is connected/disconnected to a Redis or Valkey server.

**lazyInitialization**
Default value: `false`

Defines whether Redisson connects to Redis or Valkey only when the first Redis or Valkey call is made or if Redisson connects during creation of the Redisson instance.

`true` - connects to Redis or Valkey only when the first Redis or Valkey call is made 
`false` - connects to Redis or Valkey upon Redisson instance creation

**nettyThreads**
Default value: 32

Defines the number of threads shared between all internal Redis or Valkey clients used by Redisson. Netty threads are used for Redis or Valkey response decoding and command sending. `0` = `cores_amount * 2`

**nettyHook**
Default value: empty object

Netty hook applied to Netty Bootstrap and Channel objects.

**nettyExecutor**
Default value: `null`

Use the external `ExecutorService' which is used by Netty for Redis or Valkey response decoding and command sending.

**executor**
Default value: `null`

Use the external `ExecutorService` which processes all listeners of `RTopic,` `RRemoteService` invocation handlers, and `RExecutorService` tasks.

**eventLoopGroup**
Use external EventLoopGroup. EventLoopGroup processes all Netty connections tied with Redis or Valkey servers using its own threads. By default, each Redisson client creates its own EventLoopGroup. So, if there are multiple Redisson instances in the same JVM, it would be useful to share one EventLoopGroup among them.

Only `io.netty.channel.epoll.EpollEventLoopGroup`, `io.netty.channel.kqueue.KQueueEventLoopGroup` and `io.netty.channel.nio.NioEventLoopGroup` are allowed for usage.

**transportMode**
Default value: `TransportMode.NIO`

Available values:  

* `TransportMode.NIO`,  
* `TransportMode.EPOLL` - requires `netty-transport-native-epoll` lib in the classpath  
* `TransportMode.KQUEUE` - requires `netty-transport-native-kqueue` lib in the classpath  

**threads**
Default value: 16

Threads are used to execute the listener's logic of the `RTopic` object, invocation handlers of the `RRemoteService`, the `RTopic` object and `RExecutorService` tasks.

**protocol**
Default value: RESP2

Defines the Redis or Valkey protocol version. Available values: `RESP2`, `RESP3`

**lockWatchdogTimeout**
Default value: `30000`

RLock object watchdog timeout in milliseconds. This parameter is only used if an RLock object is acquired without the `leaseTimeout` parameter. The lock expires after `lockWatchdogTimeout` if the watchdog didn’t extend it to the next `lockWatchdogTimeout` time interval. This prevents infinity-locked locks due to a Redisson client crash, or any other reason why a lock can’t be released properly.

**lockWatchdogBatchSize**  

Default value: `100`

Amount of locks used by a single lock watchdog execution. This parameter is only used if lock has been acquired without leaseTimeout parameter definition. 

**checkLockSyncedSlaves**
Default value: `true`

Defines whether to check the synchronized slaves amount with the actual slaves amount after lock acquisition.

**slavesSyncTimeout**
Default value: `1000`

Defines the slaves synchronization timeout in milliseconds, applied to each operation of the RLock, RSemaphore, RPermitExpirableSemaphore objects.

**reliableTopicWatchdogTimeout**
Default value: `600000`

Reliable Topic watchdog timeout in milliseconds. Reliable Topic subscriber expires after `timeout` if the watchdog didn’t extend it to the next `timeout` time interval. This prevents the infinite growing of stored messages in a topic, due to a Redisson client crush or any other reason when a subscriber can’t consume messages anymore.

**addressResolverGroupFactory**
Default value: `org.redisson.connection.SequentialDnsAddressResolverFactory`

Allows for specifying a customized implementation of [DnsAddressResolverGroup](https://github.com/netty/netty/blob/4.1/resolver-dns/src/main/java/io/netty/resolver/dns/DnsAddressResolverGroup.java).  

Available implementations:  

* `org.redisson.connection.DnsAddressResolverGroupFactory` - uses the default DNS servers list provided by OS.
* `org.redisson.connection.SequentialDnsAddressResolverFactory` - uses the default DNS servers list provided by OS and allows to control the concurrency level of requests to DNS servers.
* `org.redisson.connection.RoundRobinDnsAddressResolverGroupFactory` - uses the default DNS servers list provided by OS in round robin mode.

**useScriptCache**
Default value: `false`

Defines whether to use the Lua-script cache on the Redis or Valkey side. Most Redisson methods are Lua-script-based, and turning this setting on could increase the speed of such methods' execution and save network traffic.

**keepPubSubOrder**
Default value: `true`

Defines whether to keep PubSub messages handling in arrival order, or to handle messages concurrently. This setting is applied only for PubSub messages per channel.

**minCleanUpDelay**
Default value: `5`

Defines the minimum delay in seconds for the cleanup process of expired entries. Applied to `JCache`, `RSetCache`, `RClusteredSetCache`, `RMapCache`, `RListMultimapCache`, `RSetMultimapCache`, `RLocalCachedMapCache`, `RClusteredLocalCachedMapCache` objects.

**maxCleanUpDelay**
Default value: `1800`

Defines maximum delay in seconds for clean up process of expired entries. Applied to `JCache`, `RSetCache`, `RClusteredSetCache`, `RMapCache`, `RListMultimapCache`, `RSetMultimapCache`,
`RLocalCachedMapCache`, `RClusteredLocalCachedMapCache` objects.

**cleanUpKeysAmount**
Default value: `100`

Defines the amount of expired keys deleted per single operation during the cleanup process of expired entries. Applied to `JCache`, `RSetCache`, `RClusteredSetCache`, `RMapCache`, `RListMultimapCache`, `RSetMultimapCache`, `RLocalCachedMapCache`,
`RClusteredLocalCachedMapCache` objects.

**meterMode**
Default value: `ALL`

Defines the Micrometer statistics collection mode.

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._  

Available values:  

* `ALL` - collect both Redis or Valkey and Redisson objects statistics
* `REDIS` - collect only Redis or Valkey statistics
* `OBJECTS` - collect only Redisson objects statistics


**meterRegistryProvider**
Default value: `null`

Defines the Micrometer registry provider used to collect various statistics for Redisson objects. Please refer to the [statistics monitoring](observability.md) sections for list of all available providers.

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._  

**useThreadClassLoader**
Default value: `true`  

Defines whether to supply ContextClassLoader of the current Thread to Codec. 

Usage of `Thread.getContextClassLoader()` may resolve `ClassNotFoundException` errors arising during Redis or Valkey response decoding. This error might occurr if Redisson is used in both Tomcat and deployed application.

**performanceMode**
Default value: `LOWER_LATENCY_MODE_2`

Defines the command processing engine performance mode. Since all values are application-specific (except for the `NORMAL` value) it’s recommended to try all of them.

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Available values:  

* `HIGHER_THROUGHPUT` - switches command processor engine to *higher
throughput* mode 
* `LOWER_LATENCY_AUTO` - switches command processor engine to *lower latency* mode and detects optimal settings automatically
* `LOWER_LATENCY_MODE_3` - switches command processor engine to *lower
latency* mode with predefined settings set #3 
* `LOWER_LATENCY_MODE_2` - switches command processor engine to *lower latency* mode with predefined settings set #2 
* `LOWER_LATENCY_MODE_1` - switches command
processor engine to *lower latency* mode with predefined settings set #1
* `NORMAL` - switches command processor engine to normal mode

## Cluster mode

Compatible with:  

* [Redis Cluster](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/)
* [Valkey Cluster](https://valkey.io/topics/cluster-spec/)
* [AWS ElastiCache Serverless](https://aws.amazon.com/elasticache/features/#Serverless)  
* [AWS ElastiCache Cluster](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/designing-elasticache-cluster.html)  
* [Amazon MemoryDB](https://aws.amazon.com/memorydb)  
* [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)  
* [Google Cloud Memorystore for Redis Cluster](https://cloud.google.com/memorystore/docs/cluster)

For multiple Cluster deployments with data replication relationship use [Multi Cluster mode](#multi-cluster-mode).

Programmatic config example:  
```java
Config config = new Config();
config.useClusterServers()
    .setScanInterval(2000) // cluster state scan interval in milliseconds
    // use "redis://" for Redis connection
    // use "valkey://" for Valkey connection
    // use "valkeys://" for Valkey SSL connection
    // use "rediss://" for Redis SSL connection
    .addNodeAddress("redis://127.0.0.1:7000", "redis://127.0.0.1:7001")
    .addNodeAddress("redis://127.0.0.1:7002");

RedissonClient redisson = Redisson.create(config);
```
### Cluster settings
Cluster connection mode is activated by the following line:  

`ClusterServersConfig clusterConfig = config.useClusterServers();`  

`ClusterServersConfig` settings listed below:

**checkSlotsCoverage**
Default value: `true`

Enables cluster slots check during Redisson startup.

**nodeAddresses**
Add a Redis or Valkey cluster node or endpoint address in `host:port` format. Redisson automatically discovers the cluster topology. Use the `rediss://` protocol for SSL connections.

**scanInterval**
Default value: `1000`

Scan interval in milliseconds. Applied to Redis or Valkey clusters topology scans.

**topicSlots**
Default value: `9`

Partitions amount used for topic partitioning. Applied to `RClusteredTopic` and `RClusteredReliableTopic` objects.

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

**slots**
Default value: `231`

Partitions amount used for data partitioning. Data partitioning supported by [Set](data-and-services/collections.md/#eviction-and-data-partitioning), [Map](data-and-services/collections.md/#eviction-local-cache-and-data-partitioning), [BitSet](data-and-services/objects.md/#data-partitioning), [Bloom filter](data-and-services/objects.md/#data-partitioning_1), [Spring Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning), [JCache](cache-api-implementations.md/#local-cache-and-data-partitioning), [Micronaut Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_4), [Quarkus Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_3) and [Hibernate Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_1) structures.

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

**readMode**
Default value: `SLAVE`

Set node type used for read operation.
Available values:  

* `SLAVE` - Read from slave nodes, uses `MASTER` if no `SLAVES` are available,  
* `MASTER` - Read from master node,  
* `MASTER_SLAVE` - Read from master and slave nodes

**subscriptionMode**
Default value: `MASTER`

Set node type used for subscription operation.
Available values:  

* `SLAVE` - Subscribe to slave nodes,  
* `MASTER` - Subscribe to master node,  

**loadBalancer**
Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Redis or Valkey servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**subscriptionConnectionMinimumIdleSize**
Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**
Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**shardedSubscriptionMode**
Default value: `AUTO`

Defines whether to use the sharded subscription feature available in Valkey or Redis 7.0 and higher. Used by `RMapCache`, `RLocalCachedMap`, `RCountDownLatch`, `RLock`, `RPermitExpirableSemaphore`, `RSemaphore`, `RLongAdder`, `RDoubleAdder`, `Micronaut Session`, `Apache Tomcat Manager` objects.

**slaveConnectionMinimumIdleSize**
Default value: `24`

Redis or Valkey `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**
Default value: `64`

Redis or Valkey `slave` node maximum connection pool size for each slave node.

**masterConnectionMinimumIdleSize**
Default value: `24`

Minimum idle connections amount per Redis or Valkey master node.

**masterConnectionPoolSize**
Default value: `64`

Redis or Valkey `master` node maximum connection pool size.

**idleConnectionTimeout**
Default value: `10000`

If a pooled connection is not used for a timeout time and the current connections amount is bigger than the minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**
Default value: `10000`

Timeout in milliseconds during connecting to any Redis or Valkey server. 

**timeout**
Default value: `3000`

Redis or Valkey server response timeout in milliseconds. Starts countdown after a Redis or Valkey command is successfully sent. 

**retryAttempts**
Default value: `3`

Error will be thrown if Redis or Valkey command can’t be sent to server
after *retryAttempts*. But if it sent successfully then *timeout* will be started.

**retryInterval**
Default value: `1500`

Time interval in milliseconds, after which another attempt to send a Redis or Valkey command will be executed.

**failedSlaveReconnectionInterval**
Default value: `3000`

Interval of Redis or Valkey Slave reconnection attempts, when it was excluded from an internal list of available servers. On each timeout event, Redisson tries to connect to the disconnected Redis or Valkey server. Value in milliseconds.

**failedSlaveNodeDetector**
Default value: `org.redisson.client.FailedConnectionDetector`

Defines the failed Redis or Valkey Slave node detector object which implements failed node detection logic via the `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Redis or Valkey node as failed if it has ongoing connection errors in the defined `checkInterval` interval (in milliseconds). Default is 180000 milliseconds.  
* `org.redisson.client.FailedCommandsDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in the defined `checkInterval` interval (in milliseconds).  
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis or Valkey node as failed if it has a certain amount of command execution timeout errors defined by `failedCommandsLimit` in the defined `checkInterval` interval in milliseconds.  

**password**
Default value: `null`

Password for Redis or Valkey server authentication.

**username**
Default value: `null`

Username for Redis or Valkey server authentication. Requires Redis 6.0 and higher.

**credentialsResolver**

Default value: empty

Defines Credentials resolver, which is invoked during connection for Redis or Valkey server authentication. Returns Credentials object per Redis or Valkey node
address, it contains `username` and `password` fields. Allows you to specify dynamically changing Redis or Valkey credentials.

**subscriptionsPerConnection**
Default value: `5`

Subscriptions per subscriber connection limit. Used by `RTopic`,
`RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`,
`RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`,
`RLocalCachedMap`, `RLocalCachedMapCache` objectsi, and Hibernate Local
Cached Region Factories. 

**clientName**
Default value: `null`

Name of client connection.

**sslProtocols**
Default value: `null`

Defines array of allowed SSL protocols.  
Example values: `TLSv1.3`, `TLSv1.2`, `TLSv1.1`, `TLSv1`

**sslVerificationMode**

Default value: `STRICT`

Defines SSL verification mode, which prevents man-in-the-middle attacks.

Available values:  

* `NONE` - No SSL certificate verification  
* `CA_ONLY` - Validate the certificate chain but ignore hostname verification  
* `STRICT` - Complete validation of the certificate chain and hostname  

**sslProvider**
Default value: `JDK`

Defines the SSL provider (JDK or OPENSSL) used to handle SSL connections. OPENSSL is considered as a faster implementation and requires [netty-tcnative-boringssl-static](https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/) to be added to the classpath.

**sslTruststore**
Default value: `null`

Defines the path to the SSL truststore. It stores certificates which is used to identify the server side of an SSL connection. SSL truststore is read on each new connection creation and can be dynamically reloaded.


**sslTruststorePassword**
Default value: `null`

Defines password for SSL truststore

**sslKeystoreType**
Default value: `null`

Defines the SSL keystore type.

**sslKeystore**
Default value: `null`

Defines the path to the SSL truststore. It stores certificates which are used to identify the server side of an SSL connections. The SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslKeystorePassword**
Default value: `null`

Defines password for the SSL keystore

**pingConnectionInterval**
Default value: `30000`

This setting allows for detecting and reconnecting broken connections, using the PING command. PING command send interval is defined in milliseconds. Useful in cases when the netty lib doesn’t invoke `channelInactive` method for closed connections. Set to `0` to disable.

**keepAlive**
Default value: `false`

Enables TCP keepAlive for connection. 

**tcpKeepAliveCount**
Default value: 0

This defines the maximum number of keepalive probes TCP should send before dropping the connection. A `0` value means to use the system's default setting.


**tcpKeepAliveIdle**
Default value: 0

Defines the time in seconds the connection needs to remain idle before
TCP starts sending keepalive probes. A 0 value means use the system's default setting.

**tcpKeepAliveInterval**
Default value: 0

Defines the time in seconds between individual keepalive probes. `0` value means use the system's default setting.

**tcpUserTimeout**
Default value: 0

Defines the maximum amount of time in milliseconds that transmitted data may remain unacknowledged or buffered data may remain untransmitted (due to zero window size) before TCP will forcibly close the connection. A 0 value means use the system's default setting.

**tcpNoDelay**
Default value: `true`

Enables TCP noDelay for connections.

**subscriptionTimeout**
Default value: 7500

Defines subscription timeout in milliseconds applied per channel
subscription.

**natMapper**
Default value: no mapper

Defines NAT mapper interface, which maps Redis or Valkey URI objects and applies to all connections. It can be used to map internal Redis or Valkey server IPs to external ones. 

Available implementations:

* `org.redisson.api.HostPortNatMapper`
* `org.redisson.api.HostNatMapper`

**nameMapper**
Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name.
Applied to all Redisson objects.

**commandMapper**
Default value: no mapper

Defines Command mapper, which maps Redis or Valkey command name to a custom name.
Applied to all Redis or Valkey commands.


### Cluster YAML config format
Below is a cluster configuration example in YAML format. All property
names matched with `ClusterServersConfig` and `Config` object property names.
```yaml
---
clusterServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
  password: null
  subscriptionsPerConnection: 5
  clientName: null
  loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  slaveConnectionMinimumIdleSize: 24
  slaveConnectionPoolSize: 64
  masterConnectionMinimumIdleSize: 24
  masterConnectionPoolSize: 64
  readMode: "SLAVE"
  subscriptionMode: "SLAVE"
  nodeAddresses:
  - "redis://127.0.0.1:7004"
  - "redis://127.0.0.1:7001"
  - "redis://127.0.0.1:7000"
  scanInterval: 1000
  pingConnectionInterval: 30000
  keepAlive: false
  tcpNoDelay: true
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Replicated mode
With Replicated mode the role of each node is polled to determine if a failover has occurred resulting in a new master. 

Compatible with:

* [AWS ElastiCache](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Replication.html) (non-clustered)
* [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/) (non-clustered)
* [Google Cloud Memorystore for Redis High availability](https://cloud.google.com/memorystore/docs/redis/high-availability)

_Use [Redisson PRO](https://redisson.pro/feature-comparison.html) if a single host bounded to multiple slaves or master and slave nodes. Compatible with [Aiven for Caching](https://aiven.io/caching) and [Aiven for Vakey](https://aiven.io/valkey) hosting._

Programmatic config example:
```java
Config config = new Config();
config.useReplicatedServers()
    .setScanInterval(2000) // master node change scan interval
    // use "redis://" for Redis connection
    // use "valkey://" for Valkey connection
    // use "valkeys://" for Valkey SSL connection
    // use "rediss://" for Redis SSL connection
    .addNodeAddress("redis://127.0.0.1:7000", "redis://127.0.0.1:7001")
    .addNodeAddress("redis://127.0.0.1:7002");

RedissonClient redisson = Redisson.create(config);
```
### Replicated settings
Replicated connection mode is activated by follow line:  

`ReplicatedServersConfig replicatedConfig = config.useReplicatedServers();`  

`Replicated ServersConfig` settings listed below:

**nodeAddresses**
Add Redis or Valkey node address in `host:port` format. Multiple nodes could be added at once. All nodes (master and slaves) should be defined. For Aiven Redis hosting single hostname is enough. Use `rediss://` protocol for SSL connection.

**scanInterval**
Default value: `1000`

Replicated nodes scan interval in milliseconds.

**loadBalancer**
Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Redis or Valkey servers.

Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**monitorIPChanges**
Default value: `false`

Check each Redis or Valkey hostname defined in the configuration for IP address
changes during the scan process.

**subscriptionConnectionMinimumIdleSize**
Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**
Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used
by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`,
`RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`,
`RLocalCachedMap`, `RLocalCachedMapCache` objects, and Hibernate Local
Cached Region Factories.

**slaveConnectionMinimumIdleSize**
Default value: `24`

Redis or Valkey `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**
Default value: `64`

Redis or Valkey `slave` node maximum connection pool size for each slave node.

**masterConnectionMinimumIdleSize**
Default value: `24`

The minimum idle connection amount is per Redis or Valkey master node.

**masterConnectionPoolSize**
Default value: `64`

Redis or Valkey `master` node maximum connection pool size.

**idleConnectionTimeout**
Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**readMode**
Default value: `SLAVE`

Set node type used for read operation.
Available values:  

* `SLAVE` - Read from slave nodes, uses MASTER if no SLAVES are available,  
* `MASTER` - Read from the master node,  
* `MASTER_SLAVE` - Read from master and slave nodes

**subscriptionMode**
Default value: `MASTER`

Set node type used for subscription operation.
Available values:  

* `SLAVE` - Subscribe to slave nodes,  
* `MASTER` - Subscribe to master node,  

**connectTimeout**
Default value: `10000`

Timeout during connecting to any Redis or Valkey server.

**timeout**
Default value: `3000`

Redis or Valkey server response timeout. It starts to count down after a Redis or Valkey command is successfully sent. Value in milliseconds. 

**retryAttempts**
Default value: `3`

An error will be thrown if a Redis or Valkey command can’t be sent to Redis or Valkey server
after *retryAttempts*. But if it is sent successfully, then *timeout* will be
started.

**retryInterval**
Default value: `1500`

Time interval after which another attempt to send a Redis or Valkey command will be executed. Value in milliseconds. 

**failedSlaveReconnectionInterval**
Default value: `3000`

The interval of Redis or Valkey Slave reconnection attempts when excluded from
the internal list of available servers. On each timeout event, Redisson tries to connect to a disconnected Redis or Valkey server. Value in milliseconds.

**failedSlaveNodeDetector**
Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Redis or Valkey Slave node detector object, which implements failed
node detection logic via `org.redisson.client.FailedNodeDetector`
interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Redis or Valkey node as failed if it has ongoing connection errors in the defined `checkInterval` interval in milliseconds. The default is 180000 milliseconds. 
* `org.redisson.client.FailedCommandsDetector` - marks the Redis or Valkey node as failed if it has a certain amount of command execution errors defined by the `failedCommandsLimit` in the defined `checkInterval` interval in milliseconds.
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis or Valkey node as failed if it has a certain amount of command execution timeout errors defined by the `failedCommandsLimit` in the defined `checkInterval` interval in milliseconds.

**database**
Default value: `0`

Database index used for Redis or Valkey connection.

**password**
Default value: `null`

Password for Redis or Valkey server authentication. 

**username**
Default value: `null`

Username for Redis or Valkey server authentication. Requires Redis 6.0 and higher.

**credentialsResolver**
Default value: empty

Defines Credentials resolver, which is invoked during connection for Redis or Valkey server authentication. Returns Credentials object per Redis or Valkey node address, it contains `username` and `password` fields. Allows to specify dynamically changing Redis credentials.

**subscriptionsPerConnection**
Default value: `5`

Subscriptions per subscribe connection limit. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**clientName**
Default value: `null`

Name of client connection.

**sslProtocols**
Default value: `null`

Defines array of allowed SSL protocols.  
Example values: `TLSv1.3`, `TLSv1.2`, `TLSv1.1`, `TLSv1`

**sslVerificationMode**

Default value: `STRICT`

Defines SSL verification mode, which prevents man-in-the-middle attacks.

Available values:  

* `NONE` - No SSL certificate verification  
* `CA_ONLY` - Validate the certificate chain but ignore hostname verification  
* `STRICT` - Complete validation of the certificate chain and hostname  

**sslProvider**
Default value: `JDK`

Defines the SSL provider (JDK or OPENSSL) used to handle SSL connections. OPENSSL considered as a faster implementation and requires [netty-tcnative-boringssl-static](https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/) to be added in the classpath.

**sslTruststore**
Default value: `null`

Defines the path to the SSL truststore. It stores certificates which are used to identify the server side of an SSL connections. The SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslTruststorePassword**
Default value: `null`

Defines password for SSL truststore.

**sslKeystoreType**
Default value: `null`

Defines SSL keystore type.

**sslKeystore**
Default value: `null`

Defines path to the SSL keystore. It stores private key and certificates corresponding to their public keys. Used if the server side of an SSL connection requires client authentication. SSL keystore is read on each new connection creation and can be dynamically reloaded.

**sslKeystorePassword**
Default value: `null`

Defines password for SSL keystore.

**pingConnectionInterval**
Default value: `30000`
 
PING command sending interval, per connection to Redis. Defined in milliseconds. Set to `0` to disable.

**keepAlive**
Default value: `false`

Enables TCP keepAlive for connections.

**tcpNoDelay**
Default value: `true`

Enables TCP noDelay for connections.

**nameMapper**
Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name. Applied to all Redisson objects.

**commandMapper**
Default value: no mapper

Defines Command mapper which maps Redis or Valkey command name to a custom name. Applied to all commands.  

### Replicated YAML config format
Below is a replicated configuration example in YAML format. All property
names are matched with `ReplicatedServersConfig` and `Config` object property names.

```yaml
---
replicatedServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
  password: null
  subscriptionsPerConnection: 5
  clientName: null
  loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  slaveConnectionMinimumIdleSize: 24
  slaveConnectionPoolSize: 64
  masterConnectionMinimumIdleSize: 24
  masterConnectionPoolSize: 64
  readMode: "SLAVE"
  subscriptionMode: "SLAVE"
  nodeAddresses:
  - "redis://redishost1:2812"
  - "redis://redishost2:2815"
  - "redis://redishost3:2813"
  scanInterval: 1000
  monitorIPChanges: false
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Single mode

Single mode can be utilized for a single instance of either a Redis or Valkey node.

Compatible with: 

 * [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/) 
 * [Google Cloud Memorystore for Redis](https://cloud.google.com/memorystore/docs/redis/).
 * [Google Cloud Memorystore for Valkey](https://cloud.google.com/memorystore/docs/valkey/).
 * [Redis on SAP BTP](https://www.sap.com/products/technology-platform/redis-on-sap-btp-hyperscaler-option.html) 
 * [IBM Cloud Databases for Redis](https://cloud.ibm.com/docs/databases-for-redis)

Programmatic config example:  
```java
// connects to 127.0.0.1:6379 by default
RedissonClient redisson = Redisson.create();

Config config = new Config();
// use "valkey+uds://" for Valkey Unix Domain Socket (UDS) connection
// use "valkey://" for Valkey connection
// use "valkeys://" for Valkey SSL connection
// use "redis+uds://" for Redis Unix Domain Socket (UDS) connection
// use "redis://" for Redis connection
// use "rediss://" for Redis SSL connection
config.useSingleServer().setAddress("redis://myredisserver:6379");
RedissonClient redisson = Redisson.create(config);
```
### Single settings

Documentation covering Redis or Valkey single server configuration is [here](https://redis.io/topics/config). 
Multiple IP bindings for a single hostname are supported in [Proxy mode](#proxy-mode)  

Single server connection mode is activated by the following line:  
`SingleServerConfig singleConfig = config.useSingleServer();`  

`SingleServerConfig` settings listed below:

**address**
Redis or Valkey server address in `host:port` format. Use `rediss://` protocol for SSL connection.

**subscriptionConnectionMinimumIdleSize**
Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**
Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**connectionMinimumIdleSize**
Default value: `24`

Minimum idle Redis or Valkey connection amount.

**connectionPoolSize**
Default value: `64`

Redis or Valkey connection maximum pool size.

**dnsMonitoringInterval**
Default value: `5000`

DNS change monitoring interval. Set `-1` to disable. Multiple IP bindings for a single hostname are supported in [Proxy mode](#proxy-mode).

**idleConnectionTimeout**
Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connection pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**
Default value: `10000`

Timeout during connecting to any Redis or Valkey server

**timeout**
Default value: `3000`

Redis or Valkey server response timeout. It starts to count down once a Redis or Valkey command is successfully sent. Value in milliseconds. 

**retryAttempts**
Default value: `3`

Error will be thrown if Redis or Valkey command can’t be sent to Redis or Valkey server
after the defined *retryAttempts*. But if it is sent successfully, then *timeout* will be started.

**retryInterval**
Default value: `1500`

Time interval after which another attempt to send the Redis or Valkey command will be executed. Value in milliseconds. 

**database**
Default value: `0`

Database index used for Redis or Valkey connection.

**password**
Default value: `null`

Password for Redis or Valkey server authentication.

**username**
Default value: `null`

Username for Redis or Valkey server authentication. Requires Redis 6.0 and higher.

**credentialsResolver**
Default value: empty

Defines Credentials resolver, which is invoked during connection for Redis or Valkey server authentication. Returns Credentials object per Redis or Valkey node address, it contains `username` and `password` fields. Allows you to specify dynamically changing Redis credentials.

**subscriptionsPerConnection**
Default value: `5`

Subscriptions per subscriber connection limit. Used by `RTopic`,
`RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionTimeout**
Default value: 7500

Defines subscription timeout in milliseconds, applied per channel
subscription.

**clientName**
Default value: `null`

Name of client connection

**sslProtocols**
Default value: `null`

Defines array of allowed SSL protocols.  
Example values: `TLSv1.3`, `TLSv1.2`, `TLSv1.1`, `TLSv1`

**sslVerificationMode**

Default value: `STRICT`

Defines SSL verification mode, which prevents man-in-the-middle attacks.

Available values:  

* `NONE` - No SSL certificate verification  
* `CA_ONLY` - Validate the certificate chain but ignore hostname verification  
* `STRICT` - Complete validation of the certificate chain and hostname  

**sslProvider**
Default value: `JDK`

Defines SSL provider (JDK or OPENSSL) used to handle SSL connections.
OPENSSL is considered as the faster implementation and requires  [netty-tcnative-boringssl-static](https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/) to be added in classpath.

**sslTruststore**
Default value: `null`

Defines the path to the SSL truststore. It stores certificates which are used to identify the server side of an SSL connections. The SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslTruststorePassword**
Default value: `null`

Defines password for SSL truststore.

**sslKeystoreType**
Default value: `null`

Defines SSL keystore type.

**sslKeystore**
Default value: `null`

Defines the path to the SSL truststore. It stores certificates which are used to identify the server side of an SSL connections. The SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslKeystorePassword**
Default value: `null`

Defines password for SSL keystore

**pingConnectionInterval**
Default value: `30000`
 
PING command sending interval, per connection to Redis. Defined in
milliseconds. Set `0` to disable.

**keepAlive**
Default value: `false`

Enables TCP keepAlive for connections.

**tcpNoDelay**
Default value: `true`

Enables TCP noDelay for connections.

**nameMapper**
Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name.
Applied to all Redisson objects.

**commandMapper**
Default value: no mapper

Defines Command mapper which maps Redis or Valkey command name to a custom name. Applied to all commands.

### Single YAML config format

Below is a single configuration example in YAML format. All property names are matched with `SingleServerConfig` and `Config` object property names.
```yaml
---
singleServerConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  password: null
  subscriptionsPerConnection: 5
  clientName: null
  address: "redis://127.0.0.1:6379"
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  connectionMinimumIdleSize: 24
  connectionPoolSize: 64
  database: 0
  dnsMonitoringInterval: 5000
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Sentinel mode

Compatible with:  

* [Redis Sentinel](https://redis.io/learn/operate/redis-at-scale/high-availability/understanding-sentinels)
* [Valkey Sentinel](https://valkey.io/topics/sentinel/)

For multiple Sentinel deployments with data replication relationship use [Multi Sentinel mode](#multi-sentinel-mode).

Programmatic config example:  

```java
Config config = new Config();
config.useSentinelServers()
    .setMasterName("mymaster")
    // use "redis://" for Redis connection
    // use "valkey://" for Valkey connection
    // use "valkeys://" for Valkey SSL connection
    // use "rediss://" for Redis SSL connection
    .addSentinelAddress("redis://127.0.0.1:26389", "redis://127.0.0.1:26379")
    .addSentinelAddress("redis://127.0.0.1:26319");

RedissonClient redisson = Redisson.create(config);
```
### Sentinel settings

Documentation covers Redis server sentinel configuration is [here](https://redis.io/topics/sentinel).  

Sentinel connection mode is activated by follow line:  
`SentinelServersConfig sentinelConfig = config.useSentinelServers();`  

`SentinelServersConfig` settings listed below:

**checkSentinelsList**
Default value: `true`

Enables sentinels list check during Redisson startup.

**dnsMonitoringInterval**
Default value: `5000`

Interval in milliseconds to check the endpoint's DNS. Set `-1` to disable.

**checkSlaveStatusWithSyncing**
Default value: `true`

Check if slave node `master-link-status` field has status `ok`.

**masterName**

Master server name used by Redis or Valkey Sentinel servers and master change monitoring task.

**addSentinelAddress**
Add Redis or Valkey Sentinel node address in `host:port` format. Multiple nodes at once could be added.

**readMode**
Default value: `SLAVE`

Set node type used for read operation.
Available values:  

* `SLAVE` - Read from slave nodes, uses MASTER if no SLAVES are available,  
* `MASTER` - Read from master node,  
* `MASTER_SLAVE` - Read from master and slave nodes

**subscriptionMode**
Default value: `SLAVE`

Set node type used for subscription operation.
Available values:  

* `SLAVE` - Subscribe to slave nodes,  
* `MASTER` - Subscribe to master node,  

**loadBalancer**
Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Redis or Valkey servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**subscriptionConnectionMinimumIdleSize**
Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**
Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**slaveConnectionMinimumIdleSize**
Default value: `24`

Redis or Valkey `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**
Default value: `64`

Redis or Valkey `slave` node maximum connection pool size for each slave node


**masterConnectionMinimumIdleSize**
Default value: `24`

Minimum idle connections amount per Redis or Valkey master node.

**masterConnectionPoolSize**
Default value: `64`

Redis or Valkey `master` node maximum connection pool size.

**idleConnectionTimeout**
Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**
Default value: `10000`

Timeout during connecting to any Redis or Valkey server. 

**timeout**
Default value: `3000`

Redis or Valkey server response timeout. Starts to count down when a command was successfully sent. Value in milliseconds. 

**retryAttempts**
Default value: `3`

Error will be thrown if Redis or Valkey command can’t be sent to Redis server after *retryAttempts*. But if it sent successfully then *timeout* will be started.

**retryInterval**
Default value: `1500`

Time interval after which another one attempt to send Redis or Valkey command will be executed. Value in milliseconds. 

**failedSlaveReconnectionInterval**

Default value: `3000`

The interval of Redis or Valkey Slave reconnection attempts when it was excluded from internal list of available servers. On each timeout event Redisson tries to connect to disconnected server. Value in milliseconds.

**failedSlaveNodeDetector**
Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Redis or Valkey Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector`
interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Redis or Valkey node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds.  
* `org.redisson.client.FailedCommandsDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.  
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.  

**database**
Default value: `0`

Database index used for Redis or Valkey connection.

**password**
Default value: `null`

Password for Redis or Valkey servers authentication.

**username**
Default value: `null`

Username for Redis or Valkey servers authentication. Requires Redis 6.0 and higher.

**sentinelPassword**
Default value: `null`

Password for Redis or Valkey Sentinel servers authentication. Used only if
Sentinel password differs from master’s and slave’s.

**sentinelUsername**
Default value: `null`

Username for Redis or Valkey Sentinel servers for authentication. Used only if
Sentinel username differs from master’s and slave’s. Requires Redis 6.0 and higher.

**credentialsResolver**
Default value: empty

Defines Credentials resolver, which is invoked during connection for Redis or Valkey server authentication. Returns Credentials object per node address, it contains `username` and `password` fields. Allows you to specify dynamically changing credentials.

**sentinelsDiscovery**
Default value: `true`

Enables sentinels discovery.

**subscriptionsPerConnection**
Default value: `5`

Subscriptions per subscribe connection limit. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionTimeout**
Default value: 7500

Defines subscription timeout in milliseconds applied per channel
subscription.

**clientName**
Default value: `null`

Name of client connection.

**sslProtocols**
Default value: `null`

Defines array of allowed SSL protocols.  
Example values: `TLSv1.3`, `TLSv1.2`, `TLSv1.1`, `TLSv1`

**sslVerificationMode**

Default value: `STRICT`

Defines SSL verification mode, which prevents man-in-the-middle attacks.

Available values:  

* `NONE` - No SSL certificate verification  
* `CA_ONLY` - Validate the certificate chain but ignore hostname verification  
* `STRICT` - Complete validation of the certificate chain and hostname  

**sslProvider**
Default value: `JDK`

Defines SSL provider (JDK or OPENSSL) used to handle SSL connections.
OPENSSL is considered as a faster implementation and requires[netty-tcnative-boringssl-static](https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/) to be added in classpath.

**sslTruststore**
Default value: `null`

Defines path to the SSL truststore. It stores certificates which is used to identify the server side of an SSL connection. SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslTruststorePassword**
Default value: `null`

Defines password for SSL truststore.

**sslKeystoreType**
Default value: `null`

Defines SSL keystore type.

**sslKeystore**
Default value: `null`

Defines path to the SSL keystore. It stores private key and certificates corresponding to their public keys. Used if the server side of an SSL connection requires client authentication. SSL keystore is read on each new connection creation and can be dynamically reloaded.

**sslKeystorePassword**
Default value: `null`

Defines password for SSL keystore.

**pingConnectionInterval**
Default value: `30000`
 
PING command sending interval per connection to Redis. Defined in
milliseconds. Set `0` to disable.

**keepAlive**
Default value: `false`

Enables TCP keepAlive for connections.

**tcpNoDelay**
Default value: `true`

Enables TCP noDelay for connections.

**natMapper**
Default value: no mapper

Defines NAT mapper interface which maps Redis or Valkey URI object and applied to all connections. Can be used to map internal Redis server IPs to external ones. 

Available implementations:  

* `org.redisson.api.HostPortNatMapper`  
* `org.redisson.api.HostNatMapper`  

**nameMapper**
Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name.
Applied to all Redisson objects.

**commandMapper**
Default value: no mapper

Defines Command mapper which maps Redis or Valkey command name to a custom name. Applied to all commands.

### Sentinel YAML config format

Below is a sentinel configuration example in YAML format. All property
names are matched with `SentinelServersConfig` and `Config` object property names.

```yaml
---
sentinelServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
  password: null
  subscriptionsPerConnection: 5
  clientName: null
  loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  slaveConnectionMinimumIdleSize: 24
  slaveConnectionPoolSize: 64
  masterConnectionMinimumIdleSize: 24
  masterConnectionPoolSize: 64
  readMode: "SLAVE"
  subscriptionMode: "SLAVE"
  sentinelAddresses:
  - "redis://127.0.0.1:26379"
  - "redis://127.0.0.1:26389"
  masterName: "mymaster"
  database: 0
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```


## Master slave mode

Programmatic config example:  
```java
Config config = new Config();
config.useMasterSlaveServers()
    // use "redis://" for Redis connection
    // use "valkey://" for Valkey connection
    // use "valkeys://" for Valkey SSL connection
    // use "rediss://" for Redis SSL connection
    .setMasterAddress("redis://127.0.0.1:6379")
    .addSlaveAddress("redis://127.0.0.1:6389", "redis://127.0.0.1:6332", "redis://127.0.0.1:6419")
    .addSlaveAddress("redis://127.0.0.1:6399");

RedissonClient redisson = Redisson.create(config);
```
### Master slave settings

Documentation covering Redis or Valkey server master/slave configuration is [here](https://redis.io/topics/replication).  

Master slave connection mode is activated by the following line:  
`MasterSlaveServersConfig masterSlaveConfig = config.useMasterSlaveServers();`  

`MasterSlaveServersConfig` settings listed below:


**dnsMonitoringInterval**
Default value: `5000`

Interval in milliseconds to check the endpoint’s DNS. Set `-1` to
disable.

**masterAddress**
Redis or Valkey master node address in `host:port` format. Use `rediss://` protocol for SSL connection.

**addSlaveAddress**
Add Redis or Valkey slave node address in `host:port` format. Multiple nodes at
once could be added. Use `rediss://` protocol for SSL connection.

**readMode**
Default value: `SLAVE`

Set node type used for read operation.
Available values:  

* `SLAVE` - Read from slave nodes, uses MASTER if no SLAVES are
available, 
* `MASTER` - Read from master node, 
* `MASTER_SLAVE` - Read from master and slave nodes

**subscriptionMode**
Default value: `SLAVE`

Set node type used for subscription operation.
Available values:  

* `SLAVE` - Subscribe to slave nodes, 
* `MASTER` - Subscribe to master node

**loadBalancer**
Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Redis or Valkey servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**subscriptionConnectionMinimumIdleSize**
Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**
Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**slaveConnectionMinimumIdleSize**
Default value: `24`

Redis or Valkey `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**
Default value: `64`

Redis or Valkey `slave` node maximum connection pool size for each slave node.

**masterConnectionMinimumIdleSize**
Default value: `24`

Minimum idle connections amount per Redis or Valkey master node.

**masterConnectionPoolSize**
Default value: `64`

Redis or Valkey `master` node maximum connection pool size.

**idleConnectionTimeout**
Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**
Default value: `10000`

Timeout during connecting to any Redis or Valkey server. 

**timeout**
Default value: `3000`

Redis or Valkey server response timeout. Starts to count down when a command was successfully sent. Value in milliseconds. 

**retryAttempts**
Default value: `3`

Error will be thrown if Redis or Valkey command can’t be sent to server after *retryAttempts*. But if it sent successfully then *timeout* will be started.

**retryInterval**
Default value: `1500`

Time interval after which another one attempt to send Redis or Valkey command will be executed. Value in milliseconds. 

**failedSlaveReconnectionInterval**
Default value: `3000`

Interval of Redis or Valkey Slave reconnection attempts when it was excluded from internal list of available servers. On each timeout event Redisson tries to connect to disconnected server. Value in milliseconds.

**failedSlaveNodeDetector**
Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Redis or Valkey Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Redis or Valkey node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds. 
* `org.redisson.client.FailedCommandsDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.

**database**
Default value: `0`

Database index used for Redis or Valkey connection.

**password**
Default value: `null`

Password for Redis or Valkey server authentication. 

**username**
Default value: `null`

Username for Redis or Valkey server authentication. Requires Redis 6.0 and higher.

**credentialsResolver**
Default value: empty

Defines Credentials resolver, which is invoked during connection for Redis or Valkey server authentication. Returns Credentials object per address, it contains `username` and `password` fields. Allows you to specify dynamically changing credentials.

**subscriptionsPerConnection**
Default value: `5`

Subscriptions per subscriber connection limit. Used by `RTopic`,
`RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`,
`RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`,
`RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local
Cached Region Factories.

**subscriptionTimeout**
Default value: 7500

Defines subscription timeout in milliseconds applied per channel
subscription.

**clientName**
Default value: `null`

Name of client connection.

**sslProtocols**
Default value: `null`

Defines array of allowed SSL protocols.  
Example values: `TLSv1.3`, `TLSv1.2`, `TLSv1.1`, `TLSv1`

**sslVerificationMode**

Default value: `STRICT`

Defines SSL verification mode, which prevents man-in-the-middle attacks.

Available values:  

* `NONE` - No SSL certificate verification  
* `CA_ONLY` - Validate the certificate chain but ignore hostname verification  
* `STRICT` - Complete validation of the certificate chain and hostname  

**sslProvider**
Default value: `JDK`

Defines SSL provider (JDK or OPENSSL) used to handle SSL connections.
OPENSSL considered as a faster implementation and requires[netty-tcnative-boringssl-static](https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/) to be added in the classpath.

**sslTruststore**
Default value: `null`

Defines path to the SSL truststore. It stores certificates which is used to identify the server side of an SSL connection. SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslTruststorePassword**
Default value: `null`

Defines password for SSL truststore.

**sslKeystoreType**
Default value: `null`

Defines SSL keystore type.

**sslKeystore**
Default value: `null`

Defines path to the SSL keystore. It stores private key and certificates corresponding to their public keys. Used if the server side of an SSL connection requires client authentication. SSL keystore is read on each new connection creation and can be dynamically reloaded.

**sslKeystorePassword**
Default value: `null`

Defines password for SSL keystore.

**pingConnectionInterval**
Default value: `30000`
 
PING command sending interval per connection to Redis. Defined in
milliseconds. Set `0` to disable.

**keepAlive**
Default value: `false`

Enables TCP keepAlive for connections.

**tcpNoDelay**
Default value: `true`

Enables TCP noDelay for connection.

**nameMapper**
Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name. Applied to all Redisson objects.

**commandMapper**
Default value: no mapper

Defines Command mapper which maps Redis or Valkey command name to a custom name. Applied to all commands.  

### Master slave YAML config format

Below is master slave configuration example in YAML format. All property names are matched with `MasterSlaveServersConfig` and `Config` object property names.
```yaml
---
masterSlaveServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
  password: null
  subscriptionsPerConnection: 5
  clientName: null
  loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  slaveConnectionMinimumIdleSize: 24
  slaveConnectionPoolSize: 64
  masterConnectionMinimumIdleSize: 24
  masterConnectionPoolSize: 64
  readMode: "SLAVE"
  subscriptionMode: "SLAVE"
  slaveAddresses:
  - "redis://127.0.0.1:6381"
  - "redis://127.0.0.1:6380"
  masterAddress: "redis://127.0.0.1:6379"
  database: 0
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Proxy mode

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Proxy mode supports single or multiple Redis or Valkey databases (including synced with active-active replication) used for read/write operations. Each Redis or Valkey hostname might be resolved to more than one IP address. 

Depending on value of [proxyMode](#proxy-mode) setting there are two modes:  

1. all nodes are primary and used for read/write operation with load balancer  
2. single primary for read/write operation and the rest are idle secondary nodes  

Failed nodes detection is managed by `scanMode` setting.

Compatible with:  

* [AWS ElastiCache Serverless](https://aws.amazon.com/elasticache/features/#Serverless)  
* [Azure Redis Cache active-active replication](https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-how-to-active-geo-replication)  
* [Redis Enterprise Multiple Active Proxy](https://docs.redis.com/latest/rs/databases/configure/proxy-policy/#about-multiple-active-proxy-support)  
* [Redis Enterprise Active-Active databases](https://docs.redis.com/latest/rs/databases/active-active/get-started/)  
* [IBM Cloud Databases for Redis High availability](https://cloud.ibm.com/docs/databases-for-redis?topic=databases-for-redis-high-availability)

Programmatic config example:  
```java
Config config = new Config();
// use "redis://" for Redis connection
// use "valkey://" for Valkey connection
// use "valkeys://" for Valkey SSL connection
// use "rediss://" for Redis SSL connection
config.useProxyServers().addAddress("redis://myredisserver1:6379", "redis://myredisserver2:6379");

RedissonClient redisson = Redisson.create(config);
```

### Proxy mode settings

Proxy servers connection mode is activated by the following line:  

`ProxyServersConfig proxyConfig = config.useProxyServers();`  

`ProxyServersConfig` settings listed below:

**addresses**
Redis or Valkey proxy servers addresses in `host:port` format. If single hostname is defined and DNS monitoring is enabled then all resolved ips are considered as proxy nodes and used by load balancer. Use `rediss://` protocol for SSL connection.

**subscriptionConnectionMinimumIdleSize**
Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**
Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**connectionMinimumIdleSize**
Default value: `24`

Minimum idle Redis or Valkey connection amount.

**connectionPoolSize**
Default value: `64`

Redis or Valkey connection maximum pool size.

**scanMode**
Default value: `PING`

Defines scan mode to detect failed Redis or Valkey nodes.
Available values:  

* `PING` - Each Redis or Valkey node is checked using PING command. If node unable to response then it considered as a failed node.  
* `PUBSUB` - Messages are sent over pubsub channel per Redis or Valkey node and should be received by all other nodes. If node unable to subscribe or receive message then it considered as a failed node.  

**proxyMode**
Default value: `ALL_ACTIVE`

Defines proxy mode.  
Available values:  

* `FIRST_ACTIVE` - Primary (active) database is a first address in the list of addresses and the rest are idle secondary nodes used after failover.  
* `ALL_ACTIVE` - All databases are primary (active) and used for read/write operations.  

**scanInterval**
Default value: `5000`

Defines proxy nodes scan interval in milliseconds. `0` means disable.

**scanTimeout**
Default value: `3000`

Defines proxy nodes scan timeout in milliseconds applied per Redis or Valkey node.

**dnsMonitoringInterval**
Default value: `5000`

DNS change monitoring interval. Set `-1` to disable.

**idleConnectionTimeout**
Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**
Default value: `10000`

Timeout during connecting to any Redis or Valkey server.

**timeout**
Default value: `3000`

Redis or Valkey server response timeout. Starts to count down when a command was successfully sent. Value in milliseconds. 

**retryAttempts**
Default value: `3`

Error will be thrown if Redis or Valkey  ommand can’t be sent to a server after *retryAttempts*. But if it sent successfully then *timeout* will be started.

**retryInterval**
Default value: `1500`

Time interval after which another one attempt to send Redis or Valkey command will be executed. Value in milliseconds. 

**database**
Default value: `0`

Database index used for Redis or Valkey connection.

**failedNodeReconnectionInterval**
When the retry interval reached Redisson tries to connect to the disconnected Redis or Valkey node. After successful reconnection Redis node is become available for read/write operations execution.

Default value: `3000`

**failedSlaveNodeDetector**
Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Redis or Valkey Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Redis or Valkey node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds. 
* `org.redisson.client.FailedCommandsDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in
milliseconds.
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.

**password**
Default value: `null`

Password for Redis or Valkey server authentication.

**username**
Default value: `null`

Username for Redis or Valkey server authentication. Requires Redis 6.0 and higher.

**credentialsResolver**

Default value: empty

Defines Credentials resolver, which is invoked during connection for Redis or Valkey server authentication. Returns Credentials object per address, it contains `username` and `password` fields. Allows you to specify dynamically changing credentials.

**subscriptionsPerConnection**
Default value: `5`

Subscriptions per subscribe connection limit. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionTimeout**
Default value: 7500

Defines subscription timeout in milliseconds applied per channel subscription.

**clientName**
Default value: `null`

Name of client connection

**sslProtocols**
Default value: `null`

Defines array of allowed SSL protocols.  
Example values: `TLSv1.3`, `TLSv1.2`, `TLSv1.1`, `TLSv1`

**sslVerificationMode**

Default value: `STRICT`

Defines SSL verification mode, which prevents man-in-the-middle attacks.

Available values:  

* `NONE` - No SSL certificate verification  
* `CA_ONLY` - Validate the certificate chain but ignore hostname verification  
* `STRICT` - Complete validation of the certificate chain and hostname  

**sslProvider**
Default value: `JDK`

Defines SSL provider (JDK or OPENSSL) used to handle SSL connections.
OPENSSL considered as a faster implementation and requires[netty-tcnative-boringssl-static](https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/) to be added in the classpath.

**sslTruststore**
Default value: `null`

Defines path to the SSL truststore. It stores certificates which is used to identify the server side of an SSL connection. SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslTruststorePassword**
Default value: `null`

Defines password for SSL truststore.

**sslKeystoreType**
Default value: `null`

Defines SSL keystore type.

**sslKeystore**
Default value: `null`

Defines path to the SSL keystore. It stores private key and certificates corresponding to their public keys. Used if the server side of an SSL connection requires client authentication. SSL keystore is read on each new connection creation and can be dynamically reloaded.

**sslKeystorePassword**
Default value: `null`

Defines password for SSL keystore.

**pingConnectionInterval**
Default value: `30000`
 
PING command sending interval per connection to Redis. Defined in milliseconds. Set `0` to disable.

**keepAlive**
Default value: `false`

Enables TCP keepAlive for connection. 

**tcpNoDelay**
Default value: `true`

Enables TCP noDelay for connection.

**loadBalancer**
Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Redis or Valkey servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**nameMapper**
Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name.
Applied to all Redisson objects.

**commandMapper**
Default value: no mapper

Defines Command mapper which maps Redis or Valkey command name to a custom name. Applied to all commands.

### Proxy mode YAML config format

Below is proxy mode configuration example in YAML format. All property
names are matched with `ProxyServersConfig` and `Config` object property names.

```yaml
---
proxyServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  password: null
  subscriptionsPerConnection: 5
  clientName: null
  addresses: "redis://127.0.0.1:6379"
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  connectionMinimumIdleSize: 24
  connectionPoolSize: 64
  database: 0
  dnsMonitoringInterval: 5000
  loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Multi cluster mode

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Supports multiple Redis or Valkey Cluster setups with active-passive data replication relationship. Replication of the primary Cluster with secondary Redis Cluster is managed by `replicationMode` setting.

Cluster with all available master nodes becomes the primary. Master nodes availability scan interval is defined by `scanInterval` setting.

Compatible with:

* [AWS ElastiCache Global Datastore](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/Redis-Global-Datastore.html).

Programmatic config example:  
```java
Config config = new Config();
config.useMultiClusterServers()
    .setScanInterval(2000) // cluster state scan interval in milliseconds
    // use "redis://" for Redis connection
    // use "valkey://" for Valkey connection
    // use "valkeys://" for Valkey SSL connection
    // use "rediss://" for Redis SSL connection
    .addAddress("redis://cluster1:7000", "redis://cluster2:70002");

RedissonClient redisson = Redisson.create(config);
```
### Multi Cluster settings

Multi clusters connection mode is activated by follow line:  

`ClusterServersConfig clusterConfig = config.useMultiClusterServers();`  
`ClusterServersConfig` settings listed below:

**checkSlotsCoverage**
Default value: `true`

Enables cluster slots check during Redisson startup.

**addresses**
Each entry is a Redis or Valkey cluster setup, which is defined by the hostname of any of nodes in cluster or endpoint. Addresses should be in `redis://host:port` format. Use `rediss://` protocol for SSL connection.

**scanInterval**
Default value: `5000`

Scan interval in milliseconds. Applied to clusters topology scan and primary and secondary clusters scan process. Handles failover between primary and secondary clusters. Cluster with all available master nodes becomes the primary.

**slots**
Default value: `231`

Partitions amount used for data partitioning. Data partitioning supported by [Set](data-and-services/collections.md/#eviction-and-data-partitioning), [Map](data-and-services/collections.md/#eviction-local-cache-and-data-partitioning), [BitSet](data-and-services/objects.md/#data-partitioning), [Bloom filter](data-and-services/objects.md/#data-partitioning_1), [Spring Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning), [JCache](cache-api-implementations.md/#local-cache-and-data-partitioning), [Micronaut Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_4), [Quarkus Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_3) and [Hibernate Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_1) structures.


**readMode**
Default value: `SLAVE`

Set node type used for read operation.
Available values:  

* `SLAVE` - Read from slave nodes, uses MASTER if no SLAVES are available,  
* `MASTER` - Read from master node,  
* `MASTER_SLAVE` - Read from master and slave nodes

**datastoreMode**
Default value: `ACTIVE_PASSIVE`

Defines Datastore mode.  
Available values:  

* `ACTIVE` - only primary (active) cluster is used  
* `ACTIVE_PASSIVE` - primary (active) cluster is used for read/write operations and secondary (passive) clusters are used for read operations only  
* `WRITE_ACTIVE_READ_PASSIVE` - Primary (active) cluster is used for write operations and secondary (passive) clusters are used for read operations only  

**subscriptionMode**
Default value: `SLAVE`

Set node type used for subscription operation.
Available values:  

* `SLAVE` - Subscribe to slave nodes,  
* `MASTER` - Subscribe to master node,  

**shardedSubscriptionMode**
Default value: `AUTO`

Defines whether to use sharded subscription feature available in Valkey or Redis 7.0 and higher. Used by `RMapCache`, `RLocalCachedMap`, `RCountDownLatch`, `RLock`, `RPermitExpirableSemaphore`, `RSemaphore`, `RLongAdder`, `RDoubleAdder`, `Micronaut Session`, `Apache Tomcat Manager` objects.

**replicationMode**
Default value: `NONE`

Defines replication of the primary Cluster with secondary Redis Clusters.  

Available values:  

* `NONE` - No replication executed by Redisson. Replication should be executed on Redis or Valkey side,  
* `SYNC` - Each Redisson method invocation which modifies data is completed only if it has been replicated to all Redis or Valkey deployments,  
* `ASYNC` - Each Redisson method invocation which modifies data doesn't wait for replication to complete on other Redis or Valkey deployments  

**loadBalancer**
Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Redis or Valkey servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**primaryDiscoveryMode**
Default value: `AUTO`

Defines primary Cluster selection mode.

Available values:  

* `FIRST_PRIMARY` - Primary database is the first address in the list of addresses
* `AUTO` - Primary database is selected if all master nodes are available

**subscriptionConnectionMinimumIdleSize**
Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**
Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories

**slaveConnectionMinimumIdleSize**
Default value: `24`

Redis or Valkey `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**
Default value: `64`

Redis or Valkey `slave` node maximum connection pool size for each slave node.

**masterConnectionMinimumIdleSize**
Default value: `24`

Minimum idle connections amount per Redis or Valkey master node.

**masterConnectionPoolSize**
Default value: `64`

Redis or Valkey `master' node maximum connection pool size.

**idleConnectionTimeout**
Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**
Default value: `10000`

Timeout in milliseconds during connecting to any Redis or Valkey server

**timeout**
Default value: `3000`

Redis or Valkey server response timeout in milliseconds. Starts to count down when a command was successfully sent. 

**retryAttempts**
Default value: `3`

Error will be thrown if Redis or Valkey command can’t be sent to a server after *retryAttempts*. But if it sent successfully then *timeout* will be started.

**retryInterval**
Default value: `1500`

Time interval in milliseconds after which another one attempt to send Redis or Valkey command will be executed.

**failedSlaveReconnectionInterval**
Default value: `3000`

Interval of Redis or Valkey Slave reconnection attempts when it was excluded from internal list of available servers. On each timeout event Redisson tries to connect to disconnected server. Value in milliseconds.

**failedSlaveNodeDetector**
Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Redis or Valkey Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface.  

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Redis or Valkey node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds.  
* `org.redisson.client.FailedCommandsDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.  
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.  

**password**
Default value: `null`

Password for Redis or Valkey server authentication.

**username**
Default value: `null`

Username for Redis or Valkey server authentication. Requires Redis 6.0 and higher.

**credentialsResolver**

Default value: empty

Defines Credentials resolver, which is invoked during connection for Redis or Valkey server authentication. Returns Credentials object per node address, it contains `username` and `password` fields. Allows you to specify dynamically changing credentials.

**subscriptionsPerConnection**
Default value: `5`

Subscriptions per subscribe connection limit. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionTimeout**
Default value: 7500

Defines subscription timeout in milliseconds applied per channel subscription.

**clientName**
Default value: `null`

Name of client connection.

**sslProtocols**
Default value: `null`

Defines array of allowed SSL protocols.  
Example values: `TLSv1.3`, `TLSv1.2`, `TLSv1.1`, `TLSv1`

**sslVerificationMode**

Default value: `STRICT`

Defines SSL verification mode, which prevents man-in-the-middle attacks.

Available values:  

* `NONE` - No SSL certificate verification  
* `CA_ONLY` - Validate the certificate chain but ignore hostname verification  
* `STRICT` - Complete validation of the certificate chain and hostname  

**sslProvider**
Default value: `JDK`

Defines SSL provider (JDK or OPENSSL) used to handle SSL connections.
OPENSSL is considered the faster implementation and requires [netty-tcnative-boringssl-static](https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/) to be added in the classpath.

**sslTruststore**
Default value: `null`

Defines the path to the SSL truststore. It stores certificates which are used to identify the server side of an SSL connections. The SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslTruststorePassword**
Default value: `null`

Defines password for SSL truststore.

**sslKeystoreType**
Default value: `null`

Defines SSL keystore type.

**sslKeystore**
Default value: `null`

Defines the path to the SSL truststore. It stores certificates which are used to identify the server side of an SSL connections. The SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslKeystorePassword**
Default value: `null`

Defines password for SSL keystore.

**pingConnectionInterval**
Default value: `30000`

This setting allows to detect and reconnect broken connections using
PING command. PING command sending interval defined in milliseconds.
Useful in cases when netty lib doesn’t invoke `channelInactive` method
for closed connections. Set `0` to disable.

**keepAlive**
Default value: `false`

Enables TCP keepAlive for connection. 

**tcpNoDelay**
Default value: `true`

Enables TCP noDelay for connection.

**natMapper**
Default value: no mapper

Defines NAT mapper interface which maps Redis or Valkey URI object and applied to all connections. Can be used to map internal Redis server IPs to external ones. Available implementations:  

* `org.redisson.api.HostPortNatMapper`  
* `org.redisson.api.HostNatMapper`  

**nameMapper**
Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name. Applied to all Redisson objects.  

**commandMapper**
Default value: no mapper

Defines Command mapper which maps Redis or Valkey command name to a custom name. Applied to all commands.  

### Multi Cluster YAML config format

Below is cluster configuration example in YAML format. All property
names are matched with `ClusterServersConfig` and `Config` object property names.

```yaml
---
multiClusterServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
  password: null
  subscriptionsPerConnection: 5
  clientName: null
  loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  slaveConnectionMinimumIdleSize: 24
  slaveConnectionPoolSize: 64
  masterConnectionMinimumIdleSize: 24
  masterConnectionPoolSize: 64
  readMode: "SLAVE"
  datastoreMode: "ACTIVE_PASSIVE"
  subscriptionMode: "SLAVE"
  addresses:
  - "redis://cluster1:7004"
  - "redis://cluster2:7001"
  - "redis://cluster3:7000"
  scanInterval: 5000
  pingConnectionInterval: 30000
  keepAlive: false
  tcpNoDelay: true
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Multi Sentinel mode

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Supports multiple Redis or Valkey Sentinel setups with active-passive data replication.  

Replication of primary Sentinel deployment with secondary Sentinel deployments is managed by `replicationMode` setting. First sentinel host belongs to the active Sentinel setup and others to Passive Sentinel Setups.

Programmatic config example:  
```java
Config config = new Config();
config.useMultiSentinelServers()
    .setReplicationMode(ReplicationMode.ASYNC)
    .setMasterName("mymaster")
    // use "redis://" for Redis connection
    // use "valkey://" for Valkey connection
    // use "valkeys://" for Valkey SSL connection
    // use "rediss://" for Redis SSL connection
    .addSentinelAddress("redis://sentinel_primary_cluster:26389", 
                        "redis://sentinel_secondary_cluster1:26379", 
                        "redis://sentinel_secondary_cluster2:26379")

RedissonClient redisson = Redisson.create(config);
```
### Multi Sentinel settings

Documentation covering Redis or Valkey server sentinel configuration is [here](https://redis.io/topics/sentinel).  

Multi Sentinel connection mode is activated by follow line:  
`MultiSentinelServersConfig sentinelConfig = config.useMultiSentinelServers();`  

`MultiSentinelServersConfig` settings listed below:

**replicationMode**
Default value: `NONE`

Defines replication of primary Sentinel deployment with secondary Redis Sentinel deployments.  

Available values:  

* `NONE` - No replication executed by Redisson. Replication should be executed on Redis or Valkey side,  
* `SYNC` - Each Redisson method invocation which modifies data is completed only if it has been replicated to all Redis or Valkey deployments,  
* `ASYNC` - Each Redisson method invocation which modifies data doesn't wait for replication to complete on other Redis or Valkey deployments  

**checkSentinelsList**
Default value: `true`

Enables sentinels list check during Redisson startup.

**dnsMonitoringInterval**
Default value: `5000`

Interval in milliseconds to check the endpoint's DNS. Set `-1` to disable.

**checkSlaveStatusWithSyncing**
Default value: `true`

Check if slave node `master-link-status` field has status `ok`.

**masterName**
Master server name used by Sentinel servers and master change monitoring task.

**addSentinelAddress**
Add Sentinel node address in `host:port` format. Multiple nodes at once could be added.

**readMode**
Default value: `SLAVE`

Set node type used for read operation.
Available values:  

* `SLAVE` - Read from slave nodes, uses MASTER if no SLAVES are available,  
* `MASTER` - Read from master node,  
* `MASTER_SLAVE` - Read from master and slave nodes

**subscriptionMode**
Default value: `SLAVE`

Set node type used for subscription operation.
Available values:  

* `SLAVE` - Subscribe to slave nodes,  
* `MASTER` - Subscribe to master node,  

**loadBalancer**
Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Redis or Valkey servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**subscriptionConnectionMinimumIdleSize**
Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**
Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**slaveConnectionMinimumIdleSize**
Default value: `24`

Redis or Valkey `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**
Default value: `64`

Redis or Valkey `slave` node maximum connection pool size for <b>each</b> slave node.

**masterConnectionMinimumIdleSize**
Default value: `24`

Minimum idle connections amount per Redis or Valkey master node.

**masterConnectionPoolSize**
Default value: `64`

Redis or Valkey `master` node maximum connection pool size.

**idleConnectionTimeout**
Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**
Default value: `10000`

Timeout during connecting to any Redis or Valkey server.

**timeout**
Default value: `3000`

Redis or Valkey server response timeout. Starts to count down when a command was successfully sent. Value in milliseconds. 

**retryAttempts**
Default value: `3`

Error will be thrown if Redis or Valkey command can’t be sent to a server after *retryAttempts*. But if it sent successfully then *timeout* will be started.

**retryInterval**
Default value: `1500`

Time interval after which another one attempt to send Redis or Valkey command will be executed. Value in milliseconds.


**failedSlaveReconnectionInterval**

Default value: `3000`

Interval of Redis or Valkey Slave reconnection attempts when it was excluded from internal list of available servers. On each timeout event Redisson tries to connect to disconnected server. Value in milliseconds.

**failedSlaveNodeDetector**
Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Redis or Valkey Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Redis or Valkey node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds. 
* `org.redisson.client.FailedCommandsDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.

**database**
Default value: `0`

Database index used for Redis or Valkey connections.

**password**
Default value: `null`

Password for Redis or Valkey servers authentication.

**username**
Default value: `null`

Username for Redis or Valkey servers authentication. Requires Redis 6.0 and higher.

**sentinelPassword**
Default value: `null`

Password for Sentinel servers authentication. Used only if Sentinel password differs from master's and slave's.

**sentinelUsername**
Default value: `null`

Username for Sentinel servers for authentication. Used only if Sentinel username differs from master's and slave's. Requires Redis 6.0 and higher.

**credentialsResolver**
Default value: empty

Defines Credentials resolver, which is invoked during connection for Redis or Valkey server authentication. Returns Credentials object per node address, it contains `username` and `password` fields. Allows you to specify dynamically changing credentials.

**sentinelsDiscovery**
Default value: `true`

Enables sentinels discovery.

**subscriptionsPerConnection**
Default value: `5`

Subscriptions per subscribe connection limit. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionTimeout**
Default value: 7500

Defines subscription timeout in milliseconds applied per channel subscription.

**clientName**
Default value: `null`

Name of client connection.

**sslProtocols**
Default value: `null`

Defines array of allowed SSL protocols.  
Example values: `TLSv1.3`, `TLSv1.2`, `TLSv1.1`, `TLSv1`

**sslVerificationMode**

Default value: `STRICT`

Defines SSL verification mode, which prevents man-in-the-middle attacks.

Available values:  

* `NONE` - No SSL certificate verification  
* `CA_ONLY` - Validate the certificate chain but ignore hostname verification  
* `STRICT` - Complete validation of the certificate chain and hostname  

**sslProvider**
Default value: `JDK`

Defines SSL provider (JDK or OPENSSL) used to handle SSL connections.
OPENSSL considered as a faster implementation and requires [netty-tcnative-boringssl-static](https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/) to be added in the classpath.

**sslTruststore**
Default value: `null`

Defines the path to the SSL truststore. It stores certificates which are used to identify the server side of an SSL connections. The SSL truststore is read on each new connection creation and can be dynamically reloaded.

**sslTruststorePassword**
Default value: `null`

Defines password for SSL truststore.

**sslKeystoreType**
Default value: `null`

Defines SSL keystore type.

**sslKeystore**
Default value: `null`

Defines the path to the SSL keystore. It stores certificates which are used to identify the server side of an SSL connections. The SSL keystore is read on each new connection creation and can be dynamically reloaded.

**sslKeystorePassword**
Default value: `null`

Defines password for SSL keystore.

**pingConnectionInterval**
Default value: `30000`
 
PING command sending interval per connection to Redis. Defined in milliseconds. Set `0` to disable.

**keepAlive**
Default value: `false`

Enables TCP keepAlive for connection. 

**tcpNoDelay**
Default value: `true`

Enables TCP noDelay for connection.

**natMapper**
Default value: no mapper

Defines NAT mapper interface which maps Redis or Valkey URI object and applied to all connections. Can be used to map internal Redis or Valkey server IPs to external ones. Available implementations:  

* `org.redisson.api.HostPortNatMapper`  
* `org.redisson.api.HostNatMapper`  

**nameMapper**
Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name. Applied to all Redisson objects.

**commandMapper**
Default value: no mapper

Defines Command mapper which maps Redis or Valkey command name to a custom name. Applied to all commands.  

### Multi Sentinel YAML config format

Below is a sentinel configuration example in YAML format. All property
names are matched with `MultiSentinelServersConfig` and `Config` object
property names.

```yaml
---
multiSentinelServersConfig:
  replicationMode: "ASYNC"
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 3
  retryInterval: 1500
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
  password: null
  subscriptionsPerConnection: 5
  clientName: null
  loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  slaveConnectionMinimumIdleSize: 24
  slaveConnectionPoolSize: 64
  masterConnectionMinimumIdleSize: 24
  masterConnectionPoolSize: 64
  readMode: "SLAVE"
  subscriptionMode: "SLAVE"
  sentinelAddresses:
  - "redis://127.0.0.1:26379"
  - "redis://127.0.0.1:26389"
  masterName: "mymaster"
  database: 0
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```
