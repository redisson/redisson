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

Valkey or Redis data codec. Used during read/write data operations. Several implementations are [available](data-and-services/data-serialization.md).

**connectionListener**

Default value: `null`

The connection listener, which is triggered when Redisson is connected/disconnected to a Valkey or Redis server.

**lazyInitialization**

Default value: `false`

Defines whether Redisson connects to Valkey or Redis only when the first Valkey or Redis call is made or if Redisson connects during creation of the Redisson instance.

`true` - connects to Valkey or Redis only when the first Valkey or Redis call is made 
`false` - connects to Valkey or Redis upon Redisson instance creation

**nettyThreads**

Default value: 32

Defines the number of threads shared between all internal Valkey or Redis clients used by Redisson. Netty threads are used for Valkey or Redis response decoding and command sending. `0` = `cores_amount * 2`

**nettyHook**

Default value: empty object

Netty hook applied to Netty Bootstrap and Channel objects.

**nettyExecutor**

Default value: `null`

Use the external `ExecutorService' which is used by Netty for Valkey or Redis response decoding and command sending.

**executor**

Default value: `null`

Use the external `ExecutorService` which processes all listeners of `RTopic,` `RRemoteService` invocation handlers, and `RExecutorService` tasks.

**eventLoopGroup**

Use external EventLoopGroup. EventLoopGroup processes all Netty connections tied with Valkey or Redis servers using its own threads. By default, each Redisson client creates its own EventLoopGroup. So, if there are multiple Redisson instances in the same JVM, it would be useful to share one EventLoopGroup among them.

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

Defines the Valkey or Redis protocol version. Available values: `RESP2`, `RESP3`

**valkeyCapabilities**

Default value: EMPTY

Allows to declare which Valkey capabilities should be supported. Available values: 

- `REDIRECT` - This option indicates that the client is capable of handling redirect messages.

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

Default value: `true`

Defines whether to use the Lua-script cache on the Valkey or Redis side. Most Redisson methods are Lua-script-based, and turning this setting on could increase the speed of such methods' execution and save network traffic.

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

**useThreadClassLoader**

Default value: `true`  

Defines whether to supply ContextClassLoader of the current Thread to Codec. 

Usage of `Thread.getContextClassLoader()` may resolve `ClassNotFoundException` errors arising during Valkey or Redis response decoding. This error might occurr if Redisson is used in both Tomcat and deployed application.

**registrationKey**

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._  

Defines the license key for the Redisson PRO version. Open source version doesn't require it.

Can be defined as `redisson.pro.key` system property. Example of definition in JVM command-line:

`java ... -Dredisson.pro.key=YYYYY`

**meterMode**

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._  

Default value: `ALL`

Defines the Micrometer statistics collection mode.

Available values:  

* `ALL` - collect both Valkey or Redis and Redisson objects statistics
* `REDIS` - collect only Valkey or Redis statistics
* `OBJECTS` - collect only Redisson objects statistics


**meterRegistryProvider**

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._  

Default value: `null`

Defines the Micrometer registry provider used to collect various statistics for Redisson objects. Please refer to the [statistics monitoring](observability.md) sections for list of all available providers.

**performanceMode**

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._  

Default value: `LOWER_LATENCY_MODE_2`

Defines the command processing engine performance mode. Since all values are application-specific (except for the `NORMAL` value) it’s recommended to try all of them.

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

**sslCiphers**

Default value: `null`

Defines SSL ciphers.  

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

Defines the path to the SSL truststore. It stores certificates which is used to identify the server side of an SSL connection. SSL truststore is read on each new connection creation and can be dynamically reloaded. Supported formats: JKS, PKCS#12, PEM.

The truststore should contain:

* CA certificates - Root or intermediate Certificate Authority certificates that signed the Valkey or Redis server certificates
* Self-signed certificates - If your Valkey or Redis servers use self-signed certificates, you'd include those directly
* Server certificates - The actual certificates used by your Valkey or Redis instances

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

**password**

Default value: `null`

Password for Valkey or Redis server authentication.

**username**

Default value: `null`

Username for Valkey or Redis server authentication. Requires Redis 6.0 and higher.

**credentialsResolver**

Default value: empty

Defines Credentials resolver, which is invoked during connection for Valkey or Redis server authentication. Returns Credentials object per Valkey or Redis node address, it contains `username` and `password` fields. Allows you to specify dynamically changing Valkey or Redis credentials.

Available implementations:  

* `org.redisson.config.EntraIdCredentialsResolver`  

**nameMapper**

Default value: no mapper

Defines Name mapper which maps Redisson object name to a custom name.
Applied to all Redisson objects.

**commandMapper**

Default value: no mapper

Defines Command mapper, which maps Valkey or Redis command name to a custom name.
Applied to all Valkey or Redis commands.

**tcpKeepAlive**

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

## Cluster mode

Compatible with:  

* [Valkey Cluster](https://valkey.io/topics/cluster-spec/)
* [Redis Cluster](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/)
* [AWS ElastiCache Serverless](https://aws.amazon.com/elasticache/features/#Serverless)  
* [AWS ElastiCache Cluster](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/designing-elasticache-cluster.html)  
* [Amazon MemoryDB](https://aws.amazon.com/memorydb)  
* [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)  
* [Azure Managed Redis (OSS clustering)](https://learn.microsoft.com/en-us/azure/redis/)  
* [Google Cloud Memorystore for Redis Cluster](https://cloud.google.com/memorystore/docs/cluster)
* [Oracle OCI Cache](https://docs.oracle.com/en-us/iaas/Content/ocicache/managingclusters.htm)

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

Add a Valkey or Redis cluster node or endpoint address in `host:port` format. Redisson automatically discovers the cluster topology. Use the `rediss://` protocol for SSL connections.

**scanInterval**

Default value: `1000`

Scan interval in milliseconds. Applied to Valkey or Redis clusters topology scans.

**topicSlots**

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Default value: `9`

Partitions amount used for topic partitioning. Applied to `RClusteredTopic` and `RClusteredReliableTopic` objects.

**slots**

_This setting is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Default value: `231`

Partitions amount used for data partitioning. Data partitioning supported by [Set](data-and-services/collections.md/#eviction-and-data-partitioning), [Map](data-and-services/collections.md/#eviction-local-cache-and-data-partitioning), [BitSet](data-and-services/objects.md/#data-partitioning), [Bloom filter](data-and-services/objects.md/#data-partitioning_1), [Spring Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning), [JCache](cache-api-implementations.md/#local-cache-and-data-partitioning), [Micronaut Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_4), [Quarkus Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_3) and [Hibernate Cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_1) structures.

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

Сonnection load balancer for multiple Valkey or Redis servers.
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

Valkey or Redis `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**

Default value: `64`

Valkey or Redis `slave` node maximum connection pool size for each slave node.

**masterConnectionMinimumIdleSize**

Default value: `24`

Minimum idle connections amount per Valkey or Redis master node.

**masterConnectionPoolSize**

Default value: `64`

Valkey or Redis `master` node maximum connection pool size.

**idleConnectionTimeout**

Default value: `10000`

If a pooled connection is not used for a timeout time and the current connections amount is bigger than the minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**

Default value: `10000`

Timeout in milliseconds during connecting to any Valkey or Redis server. 

**timeout**

Default value: `3000`

Valkey or Redis server response timeout in milliseconds. Starts countdown after a Valkey or Redis command is successfully sent. 

**retryAttempts**

Default value: `4`

Error will be thrown if Valkey or Redis command can’t be sent to server
after *retryAttempts*. But if it was sent successfully then *timeout* will be started.

**retryDelay**

Default value: `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`

Defines the delay strategy for a new attempt to send a command.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**reconnectionDelay**

Default value: `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`

Defines the delay strategy for a new attempt to reconnect a connection.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.


**failedSlaveReconnectionInterval**

Default value: `3000`

Interval of Valkey or Redis Slave reconnection attempts, when it was excluded from an internal list of available servers. On each timeout event, Redisson tries to connect to the disconnected Valkey or Redis server. Value in milliseconds.

**failedSlaveNodeDetector**

Default value: `org.redisson.client.FailedConnectionDetector`

Defines the failed Valkey or Redis Slave node detector object which implements failed node detection logic via the `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Valkey or Redis node as failed if it has ongoing connection errors in the defined `checkInterval` interval (in milliseconds). Default is 180000 milliseconds.  
* `org.redisson.client.FailedCommandsDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in the defined `checkInterval` interval (in milliseconds).  
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Valkey or Redis node as failed if it has a certain amount of command execution timeout errors defined by `failedCommandsLimit` in the defined `checkInterval` interval in milliseconds.  

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

**pingConnectionInterval**

Default value: `30000`

This setting allows for detecting and reconnecting broken connections, using the PING command. PING command send interval is defined in milliseconds. Useful in cases when the netty lib doesn’t invoke `channelInactive` method for closed connections. Set to `0` to disable.

**subscriptionTimeout**

Default value: 7500

Defines subscription timeout in milliseconds applied per channel
subscription.

**natMapper**

Default value: no mapper

Defines NAT mapper interface, which maps Valkey or Redis URI objects and applies to all connections. It can be used to map internal Valkey or Redis server IPs to external ones. 

Available implementations:

* `org.redisson.api.HostPortNatMapper`
* `org.redisson.api.HostNatMapper`

### Cluster YAML config format
Below is a cluster configuration example in YAML format. All property
names matched with `ClusterServersConfig` and `Config` object property names.
```yaml
---
clusterServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 4
  retryDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT1S, maxDelay: PT2S}
  reconnectionDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT0.1S, maxDelay: PT10S}
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
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
tcpKeepAlive: false
tcpNoDelay: true
password: null
username: null
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

_Use [Redisson PRO](https://redisson.pro/feature-comparison.html) if a single hostname resolves to multiple master or slave nodes. Compatible with [Aiven for Caching](https://aiven.io/caching) and [Aiven for Vakey](https://aiven.io/valkey) hosting._

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

Add Valkey or Redis node address in `host:port` format. Multiple nodes could be added at once. All nodes (master and slaves) should be defined. For Aiven Redis hosting single hostname is enough. Use `rediss://` protocol for SSL connection.

**scanInterval**

Default value: `1000`

Replicated nodes scan interval in milliseconds.

**loadBalancer**

Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Valkey or Redis servers.

Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**monitorIPChanges**

Default value: `false`

Check each Valkey or Redis hostname defined in the configuration for IP address
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

Valkey or Redis `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**

Default value: `64`

Valkey or Redis `slave` node maximum connection pool size for each slave node.

**masterConnectionMinimumIdleSize**

Default value: `24`

The minimum idle connection amount is per Valkey or Redis master node.

**masterConnectionPoolSize**

Default value: `64`

Valkey or Redis `master` node maximum connection pool size.

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

Timeout during connecting to any Valkey or Redis server.

**timeout**

Default value: `3000`

Valkey or Redis server response timeout. It starts to count down after a Valkey or Redis command is successfully sent. Value in milliseconds. 

**retryAttempts**

Default value: `4`

An error will be thrown if a Valkey or Redis command can’t be sent to Valkey or Redis server
after *retryAttempts*. But if it is sent successfully, then *timeout* will be
started.

**retryDelay**

Default value: `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`

Defines the delay strategy for a new attempt to send a command.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**reconnectionDelay**

Default value: `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`

Defines the delay strategy for a new attempt to reconnect a connection.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**failedSlaveReconnectionInterval**

Default value: `3000`

The interval of Valkey or Redis Slave reconnection attempts when excluded from
the internal list of available servers. On each timeout event, Redisson tries to connect to a disconnected Valkey or Redis server. Value in milliseconds.

**failedSlaveNodeDetector**

Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Valkey or Redis Slave node detector object, which implements failed
node detection logic via `org.redisson.client.FailedNodeDetector`
interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Valkey or Redis node as failed if it has ongoing connection errors in the defined `checkInterval` interval in milliseconds. The default is 180000 milliseconds. 
* `org.redisson.client.FailedCommandsDetector` - marks the Valkey or Redis node as failed if it has a certain amount of command execution errors defined by the `failedCommandsLimit` in the defined `checkInterval` interval in milliseconds.
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Valkey or Redis node as failed if it has a certain amount of command execution timeout errors defined by the `failedCommandsLimit` in the defined `checkInterval` interval in milliseconds.

**database**

Default value: `0`

Database index used for Valkey or Redis connection.

**subscriptionsPerConnection**

Default value: `5`

Subscriptions per subscribe connection limit. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**clientName**

Default value: `null`

Name of client connection.

**pingConnectionInterval**

Default value: `30000`

PING command sending interval, per connection to Redis. Defined in milliseconds. Set to `0` to disable.

**keepAlive**

Default value: `false`

Enables TCP keepAlive for connections.

**tcpNoDelay**

Default value: `true`

Enables TCP noDelay for connections.

### Replicated YAML config format
Below is a replicated configuration example in YAML format. All property
names are matched with `ReplicatedServersConfig` and `Config` object property names.

```yaml
---
replicatedServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 4
  retryDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT1S, maxDelay: PT2S}
  reconnectionDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT0.1S, maxDelay: PT10S}
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
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
password: null
username: null
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Single mode

Single mode can be utilized for a single instance of either a Valkey or Redis node.

Compatible with: 

 * [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)  
 * [Azure Managed Redis (Non-Clustered)](https://learn.microsoft.com/en-us/azure/redis/)  
 * [Google Cloud Memorystore for Redis](https://cloud.google.com/memorystore/docs/redis/)  
 * [Google Cloud Memorystore for Valkey](https://cloud.google.com/memorystore/docs/valkey/)  
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

Documentation covering Valkey or Redis single server configuration is [here](https://redis.io/topics/config). 
Multiple IP bindings for a single hostname are supported in [Proxy mode](#proxy-mode)  

Single server connection mode is activated by the following line:  
`SingleServerConfig singleConfig = config.useSingleServer();`  

`SingleServerConfig` settings listed below:

**address**

Valkey or Redis server address in `host:port` format. Use `rediss://` protocol for SSL connection.

**subscriptionConnectionMinimumIdleSize**

Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**

Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**connectionMinimumIdleSize**

Default value: `24`

Minimum idle Valkey or Redis connection amount.

**connectionPoolSize**

Default value: `64`

Valkey or Redis connection maximum pool size.

**dnsMonitoringInterval**

Default value: `5000`

DNS change monitoring interval. Set `-1` to disable. Multiple IP bindings for a single hostname are supported in [Proxy mode](#proxy-mode).

**idleConnectionTimeout**

Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connection pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**

Default value: `10000`

Timeout during connecting to any Valkey or Redis server

**timeout**

Default value: `3000`

Valkey or Redis server response timeout. It starts to count down once a Valkey or Redis command is successfully sent. Value in milliseconds. 

**retryAttempts**

Default value: `4`

Error will be thrown if Valkey or Redis command can’t be sent to Valkey or Redis server
after the defined *retryAttempts*. But if it is sent successfully, then *timeout* will be started.

**retryDelay**

Default value: `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`

Defines the delay strategy for a new attempt to send a command.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**reconnectionDelay**

Default value: `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`

Defines the delay strategy for a new attempt to reconnect a connection.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**database**

Default value: `0`

Database index used for Valkey or Redis connection.

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

### Single YAML config format

Below is a single configuration example in YAML format. All property names are matched with `SingleServerConfig` and `Config` object property names.
```yaml
---
singleServerConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 4
  retryDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT1S, maxDelay: PT2S}
  reconnectionDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT0.1S, maxDelay: PT10S}
  subscriptionsPerConnection: 5
  clientName: null
  address: "redis://127.0.0.1:6379"
  subscriptionConnectionMinimumIdleSize: 1
  subscriptionConnectionPoolSize: 50
  connectionMinimumIdleSize: 24
  connectionPoolSize: 64
  database: 0
  dnsMonitoringInterval: 5000
password: null
username: null
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Sentinel mode

Compatible with:  

* [Valkey Sentinel](https://valkey.io/topics/sentinel/)
* [Redis Sentinel](https://redis.io/learn/operate/redis-at-scale/high-availability/understanding-sentinels)

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


Master server name used by Valkey or Redis Sentinel servers and master change monitoring task.

**addSentinelAddress**

Add Valkey or Redis Sentinel node address in `host:port` format. Multiple nodes at once could be added.

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

Сonnection load balancer for multiple Valkey or Redis servers.
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

Valkey or Redis `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**

Default value: `64`

Valkey or Redis `slave` node maximum connection pool size for each slave node


**masterConnectionMinimumIdleSize**

Default value: `24`

Minimum idle connections amount per Valkey or Redis master node.

**masterConnectionPoolSize**

Default value: `64`

Valkey or Redis `master` node maximum connection pool size.

**idleConnectionTimeout**

Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**

Default value: `10000`

Timeout during connecting to any Valkey or Redis server. 

**timeout**

Default value: `3000`

Valkey or Redis server response timeout. Starts to count down when a command was successfully sent. Value in milliseconds. 

**retryAttempts**

Default value: `4`

Error will be thrown if Valkey or Redis command can’t be sent to Redis server after *retryAttempts*. But if it was sent successfully then *timeout* will be started.

**retryDelay**

Default value: `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`

Defines the delay strategy for a new attempt to send a command.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**reconnectionDelay**

Default value: `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`

Defines the delay strategy for a new attempt to reconnect a connection.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**failedSlaveReconnectionInterval**


Default value: `3000`

The interval of Valkey or Redis Slave reconnection attempts when it was excluded from internal list of available servers. On each timeout event Redisson tries to connect to disconnected server. Value in milliseconds.

**failedSlaveNodeDetector**

Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Valkey or Redis Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector`
interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Valkey or Redis node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds.  
* `org.redisson.client.FailedCommandsDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.  
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.  

**database**

Default value: `0`

Database index used for Valkey or Redis connection.

**sentinelUsername**

Default value: `null`

Username for Valkey or Redis Sentinel servers for authentication. Used only if
Sentinel username differs from master’s and slave’s. Requires Redis 6.0 and higher.

**credentialsResolver**

Default value: empty

Defines Credentials resolver, which is invoked during connection for Valkey or Redis server authentication. Returns Credentials object per Valkey or Redis node address, it contains `username` and `password` fields. Allows you to specify dynamically changing Valkey or Redis credentials.

Available implementations:  

* `org.redisson.config.EntraIdCredentialsResolver`  

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

Defines NAT mapper interface which maps Valkey or Redis URI object and applied to all connections. Can be used to map internal Redis server IPs to external ones. 

Available implementations:  

* `org.redisson.api.HostPortNatMapper`  
* `org.redisson.api.HostNatMapper`  

### Sentinel YAML config format

Below is a sentinel configuration example in YAML format. All property
names are matched with `SentinelServersConfig` and `Config` object property names.

```yaml
---
sentinelServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 4
  retryDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT1S, maxDelay: PT2S}
  reconnectionDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT0.1S, maxDelay: PT10S}
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
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
password: null
username: null
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

Documentation covering Valkey or Redis server master/slave configuration is [here](https://redis.io/topics/replication).  

Master slave connection mode is activated by the following line:  
`MasterSlaveServersConfig masterSlaveConfig = config.useMasterSlaveServers();`  

`MasterSlaveServersConfig` settings listed below:


**dnsMonitoringInterval**

Default value: `5000`

Interval in milliseconds to check the endpoint’s DNS. Set `-1` to
disable.

**masterAddress**

Valkey or Redis master node address in `host:port` format. Use `rediss://` protocol for SSL connection.

**addSlaveAddress**

Add Valkey or Redis slave node address in `host:port` format. Multiple nodes at
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

Сonnection load balancer for multiple Valkey or Redis servers.
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

Valkey or Redis `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**

Default value: `64`

Valkey or Redis `slave` node maximum connection pool size for each slave node.

**masterConnectionMinimumIdleSize**

Default value: `24`

Minimum idle connections amount per Valkey or Redis master node.

**masterConnectionPoolSize**

Default value: `64`

Valkey or Redis `master` node maximum connection pool size.

**idleConnectionTimeout**

Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**

Default value: `10000`

Timeout during connecting to any Valkey or Redis server. 

**timeout**

Default value: `3000`

Valkey or Redis server response timeout. Starts to count down when a command was successfully sent. Value in milliseconds. 

**retryAttempts**

Default value: `4`

Error will be thrown if Valkey or Redis command can’t be sent to server after *retryAttempts*. But if it was sent successfully then *timeout* will be started.

**retryDelay**

Default value: `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`

Defines the delay strategy for a new attempt to send a command.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**reconnectionDelay**

Default value: `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`

Defines the delay strategy for a new attempt to reconnect a connection.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**failedSlaveReconnectionInterval**

Default value: `3000`

Interval of Valkey or Redis Slave reconnection attempts when it was excluded from internal list of available servers. On each timeout event Redisson tries to connect to disconnected server. Value in milliseconds.

**failedSlaveNodeDetector**

Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Valkey or Redis Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Valkey or Redis node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds. 
* `org.redisson.client.FailedCommandsDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.

**database**

Default value: `0`

Database index used for Valkey or Redis connection.

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

### Master slave YAML config format

Below is master slave configuration example in YAML format. All property names are matched with `MasterSlaveServersConfig` and `Config` object property names.
```yaml
---
masterSlaveServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 4
  retryDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT1S, maxDelay: PT2S}
  reconnectionDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT0.1S, maxDelay: PT10S}
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
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
password: null
username: null
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Proxy mode

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Proxy mode supports single or multiple Valkey or Redis databases (including synced with active-active replication) used for read/write operations. Each Valkey or Redis hostname might be resolved to more than one IP address. 

Depending on value of [proxyMode](#proxy-mode) setting there are two modes:  

1. all nodes are primary and used for read/write operation with load balancer  
2. single primary for read/write operation and the rest are idle replica nodes  

Failed nodes detection is managed by `scanMode` setting.

Compatible with:  

* [AWS ElastiCache Serverless](https://aws.amazon.com/elasticache/features/#Serverless)  
* [Azure Managed Redis (Enterprise clustering)](https://learn.microsoft.com/en-us/azure/redis/)  
* [Azure Redis Cache Active-Active replication](https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-how-to-active-geo-replication)  
* [Redis Enterprise Active-Active databases](https://redis.io/docs/latest/operate/rs/databases/active-active/get-started/)  
* [Redis Enterprise All primary shards / All nodes](https://redis.io/docs/latest/operate/rs/databases/configure/proxy-policy/)  
* [IBM Cloud Databases for Redis High availability](https://cloud.ibm.com/docs/databases-for-redis?topic=databases-for-redis-ha-dr)

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

Valkey or Redis proxy servers addresses in `host:port` format. If single hostname is defined and DNS monitoring is enabled then all resolved ips are considered as proxy nodes and used by load balancer. Use `rediss://` protocol for SSL connection.

**subscriptionConnectionMinimumIdleSize**

Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**

Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**connectionMinimumIdleSize**

Default value: `24`

Minimum idle Valkey or Redis connection amount.

**connectionPoolSize**

Default value: `64`

Valkey or Redis connection maximum pool size.

**scanMode**

Default value: `PING`

Defines scan mode to detect failed Valkey or Redis nodes.
Available values:  

* `PING` - Each Valkey or Redis node is checked using PING command. If node unable to response then it considered as a failed node.  
* `PUBSUB` - Messages are sent over pubsub channel per Valkey or Redis node and should be received by all other nodes. If node unable to subscribe or receive message then it considered as a failed node.  

**proxyMode**

Default value: `ALL_ACTIVE`

Defines proxy mode.  
Available values:  

* `FIRST_ACTIVE` - Primary (active) database is a first address in the list of addresses and the rest are idle replica nodes used after failover.  
* `ALL_ACTIVE` - All databases are primary (active) and used for read/write operations.  

**scanInterval**

Default value: `5000`

Defines proxy nodes scan interval in milliseconds. `0` means disable.

**scanTimeout**

Default value: `3000`

Defines proxy nodes scan timeout in milliseconds applied per Valkey or Redis node.

**dnsMonitoringInterval**

Default value: `5000`

DNS change monitoring interval. Set `-1` to disable.

**idleConnectionTimeout**

Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**

Default value: `10000`

Timeout during connecting to any Valkey or Redis server.

**timeout**

Default value: `3000`

Valkey or Redis server response timeout. Starts to count down when a command was successfully sent. Value in milliseconds. 

**retryAttempts**

Default value: `4`

Error will be thrown if Valkey or Redis  ommand can’t be sent to a server after *retryAttempts*. But if it was sent successfully then *timeout* will be started.

**retryDelay**

Default value: `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`

Defines the delay strategy for a new attempt to send a command.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**reconnectionDelay**

Default value: `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`

Defines the delay strategy for a new attempt to reconnect a connection.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**database**

Default value: `0`

Database index used for Valkey or Redis connection.

**failedNodeReconnectionInterval**

When the retry interval reached Redisson tries to connect to the disconnected Valkey or Redis node. After successful reconnection Redis node is become available for read/write operations execution.

Default value: `3000`

**failedSlaveNodeDetector**

Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Valkey or Redis Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Valkey or Redis node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds. 
* `org.redisson.client.FailedCommandsDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in
milliseconds.
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.

**subscriptionsPerConnection**

Default value: `5`

Subscriptions per subscribe connection limit. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionTimeout**

Default value: 7500

Defines subscription timeout in milliseconds applied per channel subscription.

**clientName**

Default value: `null`

Name of client connection

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

Сonnection load balancer for multiple Valkey or Redis servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

### Proxy mode YAML config format

Below is proxy mode configuration example in YAML format. All property
names are matched with `ProxyServersConfig` and `Config` object property names.

```yaml
---
proxyServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 4
  retryDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT1S, maxDelay: PT2S}
  reconnectionDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT0.1S, maxDelay: PT10S}
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
password: null
username: null
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Multi Cluster mode

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Supports multiple Valkey or Redis Cluster setups with active-passive data replication relationship. 

Replication of the primary Cluster with secondary Clusters is managed by `replicationMode` setting. Primary Cluster detection is managed by `primaryDiscoveryMode` setting.

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
    .addAddress("redis://cluster1:7000", "redis://cluster2:7002",  "redis://cluster3:70003");

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

Each entry is a Valkey or Redis cluster setup, which is defined by the hostname of any of nodes in cluster or endpoint. Addresses should be in `redis://host:port` format. Use `rediss://` protocol for SSL connection.

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

Defines replication of the primary Cluster with secondary Valkey or Redis Clusters.  

Available values:  

* `NONE` - No replication executed by Redisson. Replication should be executed on Valkey or Redis side,  
* `SYNC` - Each Redisson method invocation which modifies data is completed only if it has been replicated to all Valkey or Redis deployments,  
* `ASYNC` - Each Redisson method invocation which modifies data doesn't wait for replication to complete on other Valkey or Redis deployments  

**loadBalancer**

Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Valkey or Redis servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**primaryDiscoveryMode**

Default value: `AUTO`

Defines primary Cluster selection mode.

Available values:  

* `AUTO` - The primary cluster is a cluster that has all master nodes available. Master nodes availability scan interval is defined by `scanInterval` setting.
* `FIRST_PRIMARY` - The primary cluster is the first address in the list of specified addresses in configuration. No primary cluster failover detection.
* `FIRST_PRIMARY_PUBSUB_NOTIFICATION` - The primary cluster is the first address in the list of specified addresses in the configuration. The new primary cluster is switched manually by connecting to the current primary cluster and publishing a message with the new primary database address in the format `<hostname:port>` to the 'redisson:multicluster:primary' channel. This mode is useful for data migration between clusters.

**subscriptionConnectionMinimumIdleSize**

Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionPoolSize**

Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories

**slaveConnectionMinimumIdleSize**

Default value: `24`

Valkey or Redis `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**

Default value: `64`

Valkey or Redis `slave` node maximum connection pool size for each slave node.

**masterConnectionMinimumIdleSize**

Default value: `24`

Minimum idle connections amount per Valkey or Redis master node.

**masterConnectionPoolSize**

Default value: `64`

Valkey or Redis `master' node maximum connection pool size.

**idleConnectionTimeout**

Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**

Default value: `10000`

Timeout in milliseconds during connecting to any Valkey or Redis server

**timeout**

Default value: `3000`

Valkey or Redis server response timeout in milliseconds. Starts to count down when a command was successfully sent. 

**retryAttempts**

Default value: `4`

Error will be thrown if Valkey or Redis command can’t be sent to a server after *retryAttempts*. But if it was sent successfully then *timeout* will be started.

**retryDelay**

Default value: `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`

Defines the delay strategy for a new attempt to send a command.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**reconnectionDelay**

Default value: `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`

Defines the delay strategy for a new attempt to reconnect a connection.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**failedSlaveReconnectionInterval**

Default value: `3000`

Interval of Valkey or Redis Slave reconnection attempts when it was excluded from internal list of available servers. On each timeout event Redisson tries to connect to disconnected server. Value in milliseconds.

**failedSlaveNodeDetector**

Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Valkey or Redis Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface.  

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Valkey or Redis node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds.  
* `org.redisson.client.FailedCommandsDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.  
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.  

**subscriptionsPerConnection**

Default value: `5`

Subscriptions per subscribe connection limit. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionTimeout**

Default value: 7500

Defines subscription timeout in milliseconds applied per channel subscription.

**clientName**

Default value: `null`

Name of client connection.

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

Defines NAT mapper interface which maps Valkey or Redis URI object and applied to all connections. Can be used to map internal Redis server IPs to external ones. Available implementations:  

* `org.redisson.api.HostPortNatMapper`  
* `org.redisson.api.HostNatMapper`  

### Multi Cluster YAML config format

Below is cluster configuration example in YAML format. All property
names are matched with `ClusterServersConfig` and `Config` object property names.

```yaml
---
multiClusterServersConfig:
  idleConnectionTimeout: 10000
  connectTimeout: 10000
  timeout: 3000
  retryAttempts: 4
  retryDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT1S, maxDelay: PT2S}
  reconnectionDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT0.1S, maxDelay: PT10S}
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
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
tcpKeepAlive: false
tcpNoDelay: true
password: null
username: null
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## Multi Sentinel mode

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Supports multiple Valkey or Redis Sentinel setups with active-passive data replication.  

Replication of the primary Sentinel deployment with secondary Sentinel deployments is managed by `replicationMode` setting. 
The first address in the list of specified addresses in the configuration is the primary Sentinel deployment, and the others are secondary Sentinel deployments.

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

Documentation covering Valkey or Redis server sentinel configuration is [here](https://redis.io/topics/sentinel).  

Multi Sentinel connection mode is activated by follow line:  
`MultiSentinelServersConfig sentinelConfig = config.useMultiSentinelServers();`  

`MultiSentinelServersConfig` settings listed below:

**replicationMode**

Default value: `NONE`

Defines replication of primary Sentinel deployment with secondary Valkey or Redis Sentinel deployments.  

Available values:  

* `NONE` - No replication executed by Redisson. Replication should be executed on Valkey or Redis side,  
* `SYNC` - Each Redisson method invocation which modifies data is completed only if it has been replicated to all Valkey or Redis deployments,  
* `ASYNC` - Each Redisson method invocation which modifies data doesn't wait for replication to complete on other Valkey or Redis deployments  

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

Сonnection load balancer for multiple Valkey or Redis servers.
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

Valkey or Redis `slave` node minimum idle connection amount for each slave node.

**slaveConnectionPoolSize**

Default value: `64`

Valkey or Redis `slave` node maximum connection pool size for <b>each</b> slave node.

**masterConnectionMinimumIdleSize**

Default value: `24`

Minimum idle connections amount per Valkey or Redis master node.

**masterConnectionPoolSize**

Default value: `64`

Valkey or Redis `master` node maximum connection pool size.

**idleConnectionTimeout**

Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connections pool size, then it will be closed and removed from the pool. Value in milliseconds. 

**connectTimeout**

Default value: `10000`

Timeout during connecting to any Valkey or Redis server.

**timeout**

Default value: `3000`

Valkey or Redis server response timeout. Starts to count down when a command was successfully sent. Value in milliseconds. 

**retryAttempts**

Default value: `4`

Error will be thrown if Valkey or Redis command can’t be sent to a server after *retryAttempts*. But if it was sent successfully then *timeout* will be started.

**retryDelay**

Default value: `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`

Defines the delay strategy for a new attempt to send a command.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

**reconnectionDelay**

Default value: `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`

Defines the delay strategy for a new attempt to reconnect a connection.

Available implementations:  

* `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
* `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
* `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
* `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.


**failedSlaveReconnectionInterval**

Default value: `3000`

Interval of Valkey or Redis Slave reconnection attempts when it was excluded from internal list of available servers. On each timeout event Redisson tries to connect to disconnected server. Value in milliseconds.

**failedSlaveNodeDetector**

Default value: `org.redisson.client.FailedConnectionDetector`

Defines failed Valkey or Redis Slave node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Valkey or Redis node as failed if it has ongoing connection errors in defined `checkInterval` interval in milliseconds. Default is 180000 milliseconds. 
* `org.redisson.client.FailedCommandsDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution timeout errors defined by `failedCommandsLimit` in defined `checkInterval` interval in milliseconds.

**database**

Default value: `0`

Database index used for Valkey or Redis connections.

**sentinelPassword**

Default value: `null`

Password for Sentinel servers authentication. Used only if Sentinel password differs from master's and slave's.

**sentinelUsername**

Default value: `null`

Username for Sentinel servers for authentication. Used only if Sentinel username differs from master's and slave's. Requires Redis 6.0 and higher.

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

Defines NAT mapper interface which maps Valkey or Redis URI object and applied to all connections. Can be used to map internal Valkey or Redis server IPs to external ones. Available implementations:  

* `org.redisson.api.HostPortNatMapper`  
* `org.redisson.api.HostNatMapper`  

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
  retryAttempts: 4
  retryDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT1S, maxDelay: PT2S}
  reconnectionDelay: !<org.redisson.config.EqualJitterDelay> {baseDelay: PT0.1S, maxDelay: PT10S}
  failedSlaveReconnectionInterval: 3000
  failedSlaveNodeDetector: !<org.redisson.client.FailedConnectionDetector> {}
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
password: null
username: null
threads: 16
nettyThreads: 32
codec: !<org.redisson.codec.Kryo5Codec> {}
transportMode: "NIO"
```

## License key configuration

License keys are required only for Redisson PRO. Redisson Community Edition 
does not require a license key.

You can configure your license key using any of the following methods. 

1. Valkey or Redis Storage

    Allows dynamic license key updates without application restart.

    Store the license key in Valkey or Redis as a string value:

    ```bash
    valkey-cli SET redisson.pro.key "YOUR_LICENSE_KEY"
    ```

    ```bash
    redis-cli SET redisson.pro.key "YOUR_LICENSE_KEY"
    ```

    **Use case:** Centralized license management across multiple application instances.
    <br/>
    <br/>

2. JVM System Property

    Set the license key as a JVM command-line parameter:
    ```bash
    -Dredisson.pro.key=YOUR_LICENSE_KEY
    ```

    **Use case:** Suitable for containerized environments or when you want to avoid committing keys to version control.

    **Note:** Requires application restart for license renewal.
    <br/>
    <br/>

3. YAML Configuration File

    Add the `registrationKey` property to your Redisson configuration file:
    ```yaml
    registrationKey: "YOUR_LICENSE_KEY"
    ```

    **Use case:** Recommended when using externalized configuration management.

    **Note:** Requires application restart for license renewal.
    <br/>
    <br/>

4. Programmatic Configuration

    Set the license key directly in your Java code:
    ```java
    Config config = new Config();
    config.setRegistrationKey("YOUR_LICENSE_KEY");
    ```

    **Use case:** Useful when building configuration dynamically or integrating with secret management systems.

    **Note:** Requires application restart for license renewal.


**Configuration Priority**

If multiple methods are used, Redisson applies them in the following order 
(highest to lowest priority):

1. Programmatic and YAML file configuration
2. JVM system property
3. Valkey or Redis storage
