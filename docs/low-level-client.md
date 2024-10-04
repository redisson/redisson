Redisson uses high-perfomance async and lock-free Java client for Redis or Valkey. It supports both async and sync modes. The most popular use case is to execute a command not supported by Redisson yet. Please make sure that required command is not supported already with [Redis or Valkey command mapping](commands-mapping.md) list. `org.redisson.client.protocol.RedisCommands` - contains all available commands. Code example:
``` java
// Use shared EventLoopGroup only if multiple clients are used
EventLoopGroup group = new NioEventLoopGroup();

RedisClientConfig config = new RedisClientConfig();
config.setAddress("redis://localhost:6379") // or rediss:// for ssl connection
      .setPassword("myPassword")
      .setDatabase(0)
      .setClientName("myClient")
      .setGroup(group);

RedisClient client = RedisClient.create(config);
RedisConnection conn = client.connect();
//or
CompletionStage<RedisConnection> connFuture = client.connectAsync();

// execute SET command in sync way
conn.sync(StringCodec.INSTANCE, RedisCommands.SET, "test", 0);
// execute GET command in async way
conn.async(StringCodec.INSTANCE, RedisCommands.GET, "test");

conn.close()
// or
conn.closeAsync()

client.shutdown();
// or
client.shutdownAsync();
```
