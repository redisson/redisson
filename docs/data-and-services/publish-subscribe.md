## Topic
Java [RTopic](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTopic.html) object implements Publish / Subscribe mechanism based on [Redis Pub/Sub](https://redis.io/docs/latest/develop/interact/pubsub/) or [Valkey Pub/Sub](https://valkey.io/topics/pubsub/). It allows to subscribe on events published with multiple instances of `RTopic` object with the same name. 

Listeners are re-subscribed automatically after reconnection or failover. All messages sent during absence of connection are lost. 

**For applications requiring reliable delivery and advanced processing capabilities, consider using [Reliable PubSub](#reliable-pubsub).**

Code example:

=== "Sync"
    ```java
    RTopic topic = redisson.getTopic("myTopic");
    int listenerId = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
        @Override
        public void onMessage(CharSequence channel, SomeObject message) {
            //...
        }
    });
    
    // in other thread or JVM
    RTopic topic = redisson.getTopic("myTopic");
    long clientsReceivedMessage = topic.publish(new SomeObject());
    ```
=== "Async"
    ```java
    RTopicAsync topic = redisson.getTopic("myTopic");
    RFuture<Integer> listenerFuture = topic.addListenerAsync(SomeObject.class, new MessageListener<SomeObject>() {
        @Override
        public void onMessage(CharSequence channel, SomeObject message) {
            //...
        }
    });
    
    // in other thread or JVM
    RTopicAsync topic = redisson.getTopic("myTopic");
    RFuture<Long> publishFuture = topic.publishAsync(new SomeObject());
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTopicReactive topic = redisson.getTopic("myTopic");
    Mono<Integer> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
        @Override
        public void onMessage(CharSequence channel, SomeObject message) {
            //...
        }
    });
    
    // in other thread or JVM
    RTopicReactive topic = redisson.getTopic("myTopic");
    Mono<Long> publishMono = topic.publish(new SomeObject());
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTopicRx topic = redisson.getTopic("myTopic");
    Single<Integer> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
        @Override
        public void onMessage(CharSequence channel, SomeObject message) {
            //...
        }
    });
    
    // in other thread or JVM
    RTopicRx topic = redisson.getTopic("myTopic");
    Single<Long> publishMono = topic.publish(new SomeObject());
    ```

### Partitioning

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Although each Topic instance is cluster-compatible, it can be connected only to a single Redis or Valkey node which owns the topic name. That may cause the following issues:

* CPU overload on a single node. 
* Overload of network or data traffic to a single node.
* Full interruption of the message flow during failover.

Topic partitioning addresses these challenges by enabling connections to all nodes in cluster and distributing messages effectively. It brings the following benefits:

* Increases throughput of the topic.
* Minimizes interruptions during failover.
* Lowers CPU and network load on Valkey or Redis nodes.
* Scales the message flow to multiple Valkey or Redis nodes.

Partitions amount is defined through the global [topicSlots](../configuration.md) setting or per instance through `ClusteredTopicOptions.slots()` setting, which overrides the global setting.

Slots definition per instance:
```java
RClusteredTopic topic = redisson.getClusteredTopic(ClusteredTopicOptions.name("myTopic").slots(15));
```

Usage example:

```java
RClusteredTopic topic = redisson.getClusteredTopic("myTopic");
int listenerId = topic.addListener(MyObject.class, new MessageListener<MyObject>() {
    @Override
    public void onMessage(CharSequence channel, MyObject message) {
        //...
    }
});

// in other thread or JVM
RClusteredTopic topic = redisson.getClusteredTopic("myTopic");
long clientsReceivedMessage = topic.publish(new MyObject());
```

## Topic pattern
Java implementation of Redis or Valkey based [RPatternTopic](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPatternTopic.html) object. It allows to subscribe to multiple topics by specified glob-style pattern. 

Listeners are re-subscribed automatically after reconnection to a server or failover.

Pattern examples:  

* `topic?` subscribes to `topic1`, `topicA` ...
* `topic?_my` subscribes to `topic_my`, `topic123_my`, `topicTEST_my` ...
* `topic[ae]` subscribes to `topica` and `topice` only

Code example:

=== "Sync"
    ```java
    // subscribe to all topics by `topic*` pattern
    RPatternTopic patternTopic = redisson.getPatternTopic("topic*");
    int listenerId = patternTopic.addListener(Message.class, new PatternMessageListener<Message>() {
        @Override
        public void onMessage(CharSequence pattern, CharSequence channel, Message msg) {
            //...
        }
    });
    ```
=== "Async"
    ```java
    RPatternTopicAsync patternTopic = redisson.getPatternTopic("topic*");
    RFuture<Integer> listenerFuture = patternTopic.addListenerAsync(Message.class, new PatternMessageListener<Message>() {
        @Override
        public void onMessage(CharSequence pattern, CharSequence channel, Message msg) {
            //...
        }
    });
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTopicReactive patternTopic = redisson.getPatternTopic("topic*");
    Mono<Integer> listenerMono = patternTopic.addListener(Message.class, new PatternMessageListener<Message>() {
        @Override
        public void onMessage(CharSequence pattern, CharSequence channel, Message msg) {
            //...
        }
    });
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTopicRx patternTopic = redisson.getPatternTopic("topic*");
    Single<Integer> listenerSingle = patternTopic.addListener(Message.class, new PatternMessageListener<Message>() {
        @Override
        public void onMessage(CharSequence pattern, CharSequence channel, Message msg) {
            //...
        }
    });
    ```

## Sharded topic
Java implementation of Redis or Valkey based [RShardedTopic](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RShardedTopic.html) object implements Sharded Publish / Subscribe mechanism. It allows to subscribe on events published with multiple instances of `RShardedTopic` object with the same name. Subscribe/publish operations are executed only on Redis or Valkey node in Cluster which is bounded to specific topic name. Published messages via `RShardedTopic` aren't broadcasted across all nodes as for `RTopic` object. Which reduces network bandwidth usage between Redis and Valkey nodes and their CPU load, as well as their CPU load.

Listeners are re-subscribed automatically after reconnection to a server or failover. All messages sent during absence of connection are lost. 

**For applications requiring reliable delivery and advanced processing capabilities, consider using [Reliable PubSub](#reliable-pubsub).**

Code example:

=== "Sync"
    ```java
    RShardedTopic topic = redisson.getShardedTopic("myTopic");
    int listenerId = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
        @Override
        public void onMessage(CharSequence channel, SomeObject message) {
            //...
        }
    });
    
    // in other thread or JVM
    RShardedTopic topic = redisson.getShardedTopic("myTopic");
    long clientsReceivedMessage = topic.publish(new SomeObject());
    ```
=== "Async"
    ```java
    RShardedTopicAsync topic = redisson.getShardedTopic("myTopic");
    RFuture<Integer> listenerFuture = topic.addListenerAsync(SomeObject.class, new MessageListener<SomeObject>() {
        @Override
        public void onMessage(CharSequence channel, SomeObject message) {
            //...
        }
    });
    
    // in other thread or JVM
    RShardedTopicAsync topic = redisson.getShardedTopic("myTopic");
    RFuture<Long> publishFuture = topic.publishAsync(new SomeObject());
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RShardedTopicReactive topic = redisson.getShardedTopic("myTopic");
    Mono<Integer> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
        @Override
        public void onMessage(CharSequence channel, SomeObject message) {
            //...
        }
    });
    
    // in other thread or JVM
    RShardedTopicReactive topic = redisson.getShardedTopic("myTopic");
    Mono<Long> publishMono = topic.publish(new SomeObject());
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RShardedTopicRx topic = redisson.getShardedTopic("myTopic");
    Single<Integer> listenerMono = topic.addListener(SomeObject.class, new MessageListener<SomeObject>() {
        @Override
        public void onMessage(CharSequence channel, SomeObject message) {
            //...
        }
    });
    
    // in other thread or JVM
    RShardedTopicRx topic = redisson.getShardedTopic("myTopic");
    Single<Long> publishMono = topic.publish(new SomeObject());
    ```

### Partitioning

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Although each ShardedTopic instance is cluster-compatible, it can be connected only to a single Redis or Valkey node which owns the topic name. That may cause the following issues:

* CPU overload on a single node. 
* Overload of network or data traffic to a single node.
* Full interruption of the message flow during failover.

ShardedTopic partitioning addresses these challenges by enabling connections to all nodes in cluster and distributing messages effectively. It brings the following benefits:

* Increases throughput of the topic.
* Minimizes interruptions during failover.
* Lowers CPU and network load on Valkey or Redis nodes.
* Scales the message flow to multiple Valkey or Redis nodes.

Partitions amount is defined through the global [topicSlots](../configuration.md) setting or per instance through `ClusteredTopicOptions.slots()` setting, which overrides the global setting.

Slots definition per instance:
```java
RClusteredTopic topic = redisson.getClusteredTopic(ClusteredTopicOptions.name("myTopic").slots(15));
```

Usage example:

```java
RClusteredTopic topic = redisson.getClusteredTopic("myTopic");
int listenerId = topic.addListener(MyObject.class, new MessageListener<MyObject>() {
    @Override
    public void onMessage(CharSequence channel, MyObject message) {
        //...
    }
});

// in other thread or JVM
RClusteredTopic topic = redisson.getClusteredTopic("myTopic");
long clientsReceivedMessage = topic.publish(new MyObject());
```

{% include 'data-and-services/reliable-pubsub.md' %}
