**readMode**

Default value: `SLAVE`

Set node type used for read operation.
Available values:  

* `SLAVE` - Read from slave nodes, uses `MASTER` if no `SLAVES` are available,  
* `MASTER` - Read from master node,  
* `MASTER_SLAVE` - Read from master and slave nodes

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

Interval of Valkey or Redis Slave reconnection attempts, when it was excluded from an internal list of available servers. On each timeout event, Redisson tries to connect to the disconnected Valkey or Redis server. Value in milliseconds.

**fallbackLoadingToMaster**

Default value: `true`

Defines whether a read command should be redirected to the master node if a slave node returns a `LOADING` error. A slave returns this error while it is loading the dataset into memory (for example, just after startup or after a failover). When set to `true`, the command is automatically retried on the master node; when set to `false`, the `LOADING` error is propagated to the caller.
