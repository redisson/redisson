## Topic
Java implementation of Redis or Valkey based [RTopic](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTopic.html) object implements Publish / Subscribe mechanism. It allows to subscribe on events published with multiple instances of `RTopic` object with the same name. 

Listeners are re-subscribed automatically after reconnection or failover. All messages sent during absence of connection are lost. Use [Reliable Topic](#reliable-topic) for reliable delivery.

Code example:
```java
RTopic topic = redisson.getTopic("myTopic");
int listenerId = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(String channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RTopic topic = redisson.getTopic("myTopic");
long clientsReceivedMessage = topic.publish(new SomeObject());
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTopicAsync.html) interface** usage:
```java
RTopicAsync topic = redisson.getTopic("myTopic");
RFuture<Integer> listenerFuture = topic.addListenerAsync(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(String channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RTopicAsync topic = redisson.getTopic("myTopic");
RFuture<Long> publishFuture = topic.publishAsync(new SomeObject());
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTopicReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RTopicReactive topic = redisson.getTopic("myTopic");
Mono<Integer> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(String channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RTopicReactive topic = redisson.getTopic("myTopic");
Mono<Long> publishMono = topic.publish(new SomeObject());
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTopicRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RTopicRx topic = redisson.getTopic("myTopic");
Single<Integer> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(String channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RTopicRx topic = redisson.getTopic("myTopic");
Single<Long> publishMono = topic.publish(new SomeObject());
```

## Topic pattern
Java implementation of Redis or Valkey based [RPatternTopic](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPatternTopic.html) object. It allows to subscribe to multiple topics by specified glob-style pattern. 

Listeners are re-subscribed automatically after reconnection to a server or failover.

Pattern examples:  

* `topic?` subscribes to `topic1`, `topicA` ...
* `topic?_my` subscribes to `topic_my`, `topic123_my`, `topicTEST_my` ...
* `topic[ae]` subscribes to `topica` and `topice` only

Code example:
```java
// subscribe to all topics by `topic*` pattern
RPatternTopic patternTopic = redisson.getPatternTopic("topic*");
int listenerId = patternTopic.addListener(Message.class, new PatternMessageListener<Message>() {
    @Override
    public void onMessage(String pattern, String channel, Message msg) {
        //...
    }
});
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPatternTopicAsync.html) interface** usage:
```java
RPatternTopicAsync patternTopic = redisson.getPatternTopic("topic*");
RFuture<Integer> listenerFuture = patternTopic.addListenerAsync(Message.class, new PatternMessageListener<Message>() {
    @Override
    public void onMessage(String pattern, String channel, Message msg) {
        //...
    }
});
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPatternTopicReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RTopicReactive patternTopic = redisson.getPatternTopic("topic*");
Mono<Integer> listenerMono = patternTopic.addListener(Message.class, new PatternMessageListener<Message>() {
    @Override
    public void onMessage(String pattern, String channel, Message msg) {
        //...
    }
});
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPatternTopicRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RTopicRx patternTopic = redisson.getPatternTopic("topic*");
Single<Integer> listenerSingle = patternTopic.addListener(Message.class, new PatternMessageListener<Message>() {
    @Override
    public void onMessage(String pattern, String channel, Message msg) {
        //...
    }
});
```

## Sharded topic
Java implementation of Redis or Valkey based [RShardedTopic](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RShardedTopic.html) object implements Sharded Publish / Subscribe mechanism. It allows to subscribe on events published with multiple instances of `RShardedTopic` object with the same name. Subscribe/publish operations are executed only on Redis or Valkey node in Cluster which is bounded to specific topic name. Published messages via `RShardedTopic` aren't broadcasted across all nodes as for `RTopic` object. Which reduces network bandwidth and Redis or Valkey load.

Listeners are re-subscribed automatically after reconnection to a server or failover. All messages sent during absence of connection are lost. Use [Reliable Topic](#reliable-topic) for reliable delivery.

Code example:
```java
RShardedTopic topic = redisson.getShardedTopic("myTopic");
int listenerId = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(String channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RShardedTopic topic = redisson.getShardedTopic("myTopic");
long clientsReceivedMessage = topic.publish(new SomeObject());
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RShardedTopicAsync.html) interface** usage:
```java
RShardedTopicAsync topic = redisson.getShardedTopic("myTopic");
RFuture<Integer> listenerFuture = topic.addListenerAsync(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(String channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RShardedTopicAsync topic = redisson.getShardedTopic("myTopic");
RFuture<Long> publishFuture = topic.publishAsync(new SomeObject());
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RShardedTopicReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RShardedTopicReactive topic = redisson.getShardedTopic("myTopic");
Mono<Integer> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(String channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RShardedTopicReactive topic = redisson.getShardedTopic("myTopic");
Mono<Long> publishMono = topic.publish(new SomeObject());
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RShardedTopicRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RShardedTopicRx topic = redisson.getShardedTopic("myTopic");
Single<Integer> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(String channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RShardedTopicRx topic = redisson.getShardedTopic("myTopic");
Single<Long> publishMono = topic.publish(new SomeObject());
```

## Reliable Topic
Java implementation of Redis or Valkey based [RReliableTopic](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RReliableTopic.html) object implements Publish / Subscribe mechanism with reliable delivery of messages. In case of Redis or Valkey connection interruption all missed messages are delivered after reconnection to Redis. Message considered as delivered when it was received by Redisson and submited for processing by topic listeners.

Each `RReliableTopic` object instance (subscriber) has own watchdog which is started when the first listener was registered. Subscriber expires after `org.redisson.config.Config#reliableTopicWatchdogTimeout` timeout if watchdog didn't extend it to the next timeout time interval. This prevents against infinity grow of stored messages in topic due to Redisson client crash or any other reason when subscriber unable to consume messages.

Topic listeners are resubscribed automatically after reconnection to a server or failover.

Code example:
```java
RReliableTopic topic = redisson.getReliableTopic("anyTopic");
topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(CharSequence channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RReliableTopic topic = redisson.getReliableTopic("anyTopic");
long subscribersReceivedMessage = topic.publish(new SomeObject());
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RReliableTopicAsync.html) interface** usage:
```java
RReliableTopicAsync topic = redisson.getReliableTopic("anyTopic");
RFuture<String> listenerFuture = topic.addListenerAsync(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(CharSequence channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RReliableTopicAsync topic = redisson.getReliableTopic("anyTopic");
RFuture<Long> future = topic.publishAsync(new SomeObject());
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RReliableTopicReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();

RReliableTopicReactive topic = redisson.getReliableTopic("anyTopic");
Mono<String> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(CharSequence channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RReliableTopicReactive topic = redisson.getReliableTopic("anyTopic");
Mono<Long> publishMono = topic.publish(new SomeObject());
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RReliableTopicRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();

RReliableTopicRx topic = redisson.getReliableTopic("anyTopic");
Single<String> listenerRx = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
    @Override
    public void onMessage(CharSequence channel, SomeObject message) {
        //...
    }
});

// in other thread or JVM
RReliableTopicRx topic = redisson.getReliableTopic("anyTopic");
Single<Long> publisRx = topic.publish(new SomeObject());
```