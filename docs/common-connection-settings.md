**timeout**

Default value: `3000`

Valkey or Redis server response timeout. It starts to count down once a Valkey or Redis command is successfully sent. Value in milliseconds. 

**connectTimeout**

Default value: `10000`

Timeout during connecting to any Valkey or Redis server

**idleConnectionTimeout**

Default value: `10000`

If a pooled connection is not used for a timeout time and current connections amount bigger than minimum idle connection pool size, then it will be closed and removed from the pool. Value in milliseconds. 

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

**clientName**

Default value: `null`

Name of client connection

**subscriptionsPerConnection**

Default value: `5`

Subscriptions per subscriber connection limit. Used by `RTopic`,
`RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionTimeout**

Default value: 7500

Defines subscription timeout in milliseconds, applied per channel
subscription.

**subscriptionConnectionPoolSize**

Default value: `50`

Maximum connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.

**subscriptionConnectionMinimumIdleSize**

Default value: `1`

Minimum idle connection pool size for subscription (pub/sub) channels. Used by `RTopic`, `RPatternTopic`, `RLock`, `RSemaphore`, `RCountDownLatch`, `RClusteredLocalCachedMap`, `RClusteredLocalCachedMapCache`, `RLocalCachedMap`, `RLocalCachedMapCache` objects and Hibernate Local Cached Region Factories.
