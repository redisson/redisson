**readMode**

Default value: `SLAVE`

Set node type used for read operation.
Available values:  

* `SLAVE` - Read from slave nodes, uses `MASTER` if no `SLAVES` are available,  
* `MASTER` - Read from master node,  
* `MASTER_SLAVE` - Read from master and slave nodes,  
* `AZ_AFFINITY` - Read from slave nodes in the client's availability zone, then from slave nodes in any zone, then from the master node,  
* `AZ_AFFINITY_SLAVES_AND_MASTER` - Read from slave nodes in the client's availability zone, then from the master node if it is in that zone, then from slave nodes in any zone, then from the master node,  
* `AZ_AFFINITY_MASTER_SLAVE` - Read from master and slave nodes in the client's availability zone, then from master and slave nodes in any zone

The three availability zone modes require the `clientAvailabilityZone` setting. 

**clientAvailabilityZone**

Default value: `null`

Availability zone the client runs in, for example `us-east-1a`. Required by the `AZ_AFFINITY`, `AZ_AFFINITY_SLAVES_AND_MASTER` and `AZ_AFFINITY_MASTER_SLAVE` read modes, whether set as `readMode` or for a single object through `PlainOptions.readMode()`. Zone names are compared exactly.

Redisson reads the zone of each node from the `availability_zone` field of `INFO SERVER` when it sets the node up: at startup, for a slave added later and for a new master after a failover, and again when a disconnected node comes back. Valkey 8.0 and higher reports its `availability-zone` config there. A node reports no zone when it runs Redis or a Valkey version before 8.0, when its config is left empty, or when ACL denies `INFO` to the user Redisson connects with, and it is then treated as being in another zone. Zones are read in the background, so reads right after startup may reach a node before its zone is known, and until then the node counts as being in another zone. A zone read that fails is repeated every `failedSlaveReconnectionInterval` until the node answers, unless the node denies or doesn't know the `INFO` command.

These modes send every read to the first step of their order that has a node. A slave that is down or not responding keeps getting the reads of its step until `failedSlaveNodeDetector` excludes it, so with one slave per zone a faster detector than the default `FailedConnectionDetector`, such as `FailedCommandsTimeoutDetector`, is recommended.

**loadBalancer**

Default value: `org.redisson.connection.balancer.RoundRobinLoadBalancer`

Сonnection load balancer for multiple Valkey or Redis servers.
Available implementations:  

* `org.redisson.connection.balancer.CommandsLoadBalancer`  
* `org.redisson.connection.balancer.WeightedRoundRobinBalancer`  
* `org.redisson.connection.balancer.RoundRobinLoadBalancer`  
* `org.redisson.connection.balancer.RandomLoadBalancer`  

**masterConnectionPoolSize**

Default value: `64`

Valkey or Redis `master` node maximum connection pool size.

**masterConnectionMinimumIdleSize**

Default value: `24`

Minimum idle connections amount per Valkey or Redis master node.

**slaveConnectionPoolSize**

Default value: `64`

Valkey or Redis `slave` node maximum connection pool size for each slave node.

**slaveConnectionMinimumIdleSize**

Default value: `24`

Valkey or Redis `slave` node minimum idle connection amount for each slave node.

**failedSlaveNodeDetector**

Default value: `org.redisson.client.FailedConnectionDetector`

Defines the failed Valkey or Redis Slave node detector object which implements failed node detection logic via the `org.redisson.client.FailedNodeDetector` interface.

Available implementations:  

* `org.redisson.client.FailedConnectionDetector` - marks the Valkey or Redis node as failed if it has ongoing connection errors in the defined `checkInterval` interval (in milliseconds). Default is 180000 milliseconds.  
* `org.redisson.client.FailedCommandsDetector` - marks the Valkey or Redis node as failed if it has certain amount of command execution errors defined by `failedCommandsLimit` in the defined `checkInterval` interval (in milliseconds).  
* `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Valkey or Redis node as failed if it has a certain amount of command execution timeout errors defined by `failedCommandsLimit` in the defined `checkInterval` interval in milliseconds.  

**failedSlaveReconnectionInterval**

Default value: `3000`

Interval of Valkey or Redis Slave reconnection attempts, when it was excluded from an internal list of available servers. On each timeout event, Redisson tries to connect to the disconnected Valkey or Redis server. The same interval separates the tries of a node's availability zone read, see `clientAvailabilityZone`. Value in milliseconds.

**fallbackLoadingToMaster**

Default value: `true`

Defines whether a read command should be redirected to the master node if a slave node returns a `LOADING` error. A slave returns this error while it is loading the dataset into memory (for example, just after startup or after a failover). When set to `true`, the command is automatically retried on the master node; when set to `false`, the `LOADING` error is propagated to the caller.
