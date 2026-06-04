Redisson includes a low-level, high-performance, lock-free Java client for Valkey and Redis that supports both synchronous and asynchronous modes. Its most common use is to run a command that Redisson does not expose yet - before reaching for it, make sure the command really is missing by checking the [Valkey or Redis command mapping](commands-mapping.md) list. The `org.redisson.client.protocol.RedisCommands` class contains all available commands.

Create and configure a `RedisClient`. The `EventLoopGroup` should be shared only when several clients are used:

```java
EventLoopGroup group = new NioEventLoopGroup();

RedisClientConfig config = new RedisClientConfig();
config.setAddress("redis://localhost:6379") // or rediss:// for an SSL connection
      .setPassword("myPassword")
      .setDatabase(0)
      .setClientName("myClient")
      .setGroup(group);

RedisClient client = RedisClient.create(config);
```

Open a connection, synchronously or asynchronously:

```java
RedisConnection conn = client.connect();
// or
RFuture<RedisConnection> connFuture = client.connectAsync();
```

Execute commands on the connection. `sync` blocks and returns the result, while `async` returns an `RFuture`:

```java
// SET in sync mode
conn.sync(StringCodec.INSTANCE, RedisCommands.SET, "test", 0);

// GET in async mode
RFuture<String> future = conn.async(StringCodec.INSTANCE, RedisCommands.GET, "test");
```

Close the connection and shut down the client when finished. The asynchronous variants return without blocking - `closeAsync()` returns a Netty `ChannelFuture`:

```java
conn.close();
// or
conn.closeAsync();

client.shutdown();
// or
client.shutdownAsync();
```
