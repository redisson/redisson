## JMS API implementation

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Redisson implements Java Message Service API (JMS API) based on Valkey or Redis. 

[JMS 2.0 (JSR 343)](https://jakarta.ee/specifications/messaging/2.0/), [JMS 3.0](https://jakarta.ee/specifications/messaging/3.0/) and [JMS 3.1](https://jakarta.ee/specifications/messaging/3.1/) implementations are available. 

The implementation uses [RReliableQueue](data-and-services/queues.md/#reliable-queue) for point-to-point messaging and [RReliablePubSubTopic](data-and-services/publish-subscribe.md/#reliable-pubsub) for publish/subscribe messaging. It successfully passes all TCK tests.

There are two ways to obtain a `ConnectionFactory`: programmatically through the native Java API, or declaratively through JNDI. Both approaches produce the same `RedissonConnectionFactory` and support the same JMS operations once created.

### Usage

Add dependency into your project.

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
	<!-- JMS 2.0 implementation -->
    <artifactId>redisson-jms-20</artifactId>
	<!-- JMS 3.0 implementation -->
    <artifactId>redisson-jms-30</artifactId>
	<!-- JMS 3.1 implementation -->
    <artifactId>redisson-jms-31</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
// JMS 2.0 implementation
compile 'pro.redisson:redisson-jms-20:xVERSIONx'
// JMS 3.0 implementation
compile 'pro.redisson:redisson-jms-30:xVERSIONx'
// JMS 3.1 implementation
compile 'pro.redisson:redisson-jms-31:xVERSIONx'
```


### Native Java API

The native API gives full programmatic control over the connection factory, its Redisson client, and destination configuration.

#### Creating a Connection Factory

```java
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.jms.RedissonConnectionFactory;

Config config = new Config();
config.useSingleServer()
      .setAddress("redis://127.0.0.1:6379")
      .setTimeout(5000);
config.setThreads(16);

RedissonClient redisson = Redisson.create(config);
ConnectionFactory cf = new RedissonConnectionFactory(redisson);
```

The Redisson `Config` object supports all Redisson server modes: single server, master/slave, sentinel, cluster, and replicated. Refer to the Redisson documentation for the full range of options.

#### Factory-Level Settings

Queue, topic, and subscription behavior can be tuned per-destination or as a factory-wide default.

```java
RedissonConnectionFactory cf = new RedissonConnectionFactory(redisson);

// Assigns a JMS client identifier to every connection created by this factory
cf.setClientId(id);

// Configure credential validation
cf.setUser(user);
cf.setPassword(password);

// Default for all queues
cf.setQueueConfig(myDefaultQueueConfig);

// Override for a specific queue
cf.setQueueConfig("orders", ordersQueueConfig);

// Default for all topics
cf.setTopicConfig(myDefaultTopicConfig);

// Override for a specific topic
cf.setTopicConfig("events", eventsTopicConfig);

// Subscription configuration (default and per-subscription)
cf.setSubscriptionConfig(myDefaultSubscriptionConfig);
cf.setSubscriptionConfig("mySub", mySubConfig);
```

Per-destination configuration takes precedence over the default. If neither is set for a given destination, the factory returns `null` and Redisson uses its own built-in defaults.

The available properties for each configuration type are listed in the [Destination Configuration Properties](#destination-configuration-properties) section.


#### Sending and Receiving Messages

```java
try (JMSContext ctx = cf.createContext()) {
    Queue queue = ctx.createQueue("orders");

    ctx.createProducer()
       .setDeliveryDelay(1000)
       .setPriority(7)
       .send(queue, "order-456");

    String body = ctx.createConsumer(queue)
                     .receiveBody(String.class, 5000);
}
```

#### Pub/Sub with Durable Subscriptions

```java
try (JMSContext ctx = cf.createContext()) {
    Topic topic = ctx.createTopic("events");

    JMSConsumer subscriber = ctx.createDurableConsumer(topic, "my-durable-sub");

    ctx.createProducer().send(topic, "event-1");

    String event = subscriber.receiveBody(String.class, 5000);
}
```

Durable subscriptions persist across client restarts. Non-durable subscriptions are automatically deleted when the consumer closes.

#### Lifecycle

When you create a `RedissonConnectionFactory` with a Redis URL (the convenience constructor), the factory owns the Redisson client and you are responsible for shutting it down when done. When you supply your own `RedissonClient`, you manage its lifecycle independently.

The factory maintains a ref-counted internal scheduler for async delivery retry. The scheduler starts when the first connection opens and shuts down when the last connection closes. No explicit cleanup of the factory itself is required.


### Spring JMS Configuration

Spring's JMS support works with any `jakarta.jms.ConnectionFactory` implementation. Since `RedissonConnectionFactory` implements the standard JMS `ConnectionFactory`, `QueueConnectionFactory`, and `TopicConnectionFactory` interfaces, it integrates directly with Spring's `JmsTemplate`, `@JmsListener`, and `DefaultJmsListenerContainerFactory`.

#### Java Configuration

Register a `RedissonConnectionFactory` as a Spring bean and enable JMS listener processing with `@EnableJms`. This is all that is required — Spring auto-configures a `JmsTemplate` and a default `DefaultJmsListenerContainerFactory` from the `ConnectionFactory` bean:

```java
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.jms.RedissonConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;

import jakarta.jms.ConnectionFactory;

@Configuration
@EnableJms
public class JmsConfig {

    @Bean
    public ConnectionFactory connectionFactory(RedissonClient redisson) {
        RedissonConnectionFactory cf = new RedissonConnectionFactory(redisson);
        cf.setClientId("my-app");
		
        // Queue config (optional)
        cf.setQueueConfig("orders", QueueConfig.defaults()
            .deliveryLimit(5)
            .visibility(Duration.ofSeconds(60))
            .deadLetterQueueName("orders-dlq"));

        // Topic config (optional)
        cf.setTopicConfig("events", TopicConfig.defaults()
            .deliveryLimit(10)
            .retentionMode(RetentionMode.SUBSCRIPTION_REQUIRED_DELETE_PROCESSED));

        // Subscription config (optional)
        cf.setSubscriptionConfig("auditSub", SubscriptionConfig.name("auditSub")
            .deliveryLimit(20)
            .deadLetterTopicName("events-dlt"));		
		
        return cf;
    }
}
```

#### Pub/Sub with Topics

By default, Spring's `JmsTemplate` and `DefaultJmsListenerContainerFactory` target queues (point-to-point). To work with topics (pub/sub), set `pubSubDomain` to `true`.

For sending to topics, configure the `JmsTemplate`:

```java
@Bean
public JmsTemplate jmsTopicTemplate(ConnectionFactory connectionFactory) {
    JmsTemplate template = new JmsTemplate(connectionFactory);
    template.setPubSubDomain(true);
    return template;
}
```

For receiving from topics, configure a separate listener container factory:

```java
@Bean
public DefaultJmsListenerContainerFactory topicListenerFactory(
        ConnectionFactory connectionFactory) {
    DefaultJmsListenerContainerFactory factory =
            new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setPubSubDomain(true);
    factory.setClientId("my-app");
    return factory;
}
```

Then reference it in the listener:

```java
@JmsListener(destination = "events", containerFactory = "topicListenerFactory",
             subscription = "auditSub")
public void handleEvent(String event) {
    // ...
}
```

#### Spring Boot Auto-Configuration

Defining a `RedissonConnectionFactory` bean is sufficient — Spring Boot's JMS auto-configuration detects it and creates the `JmsTemplate` and default `JmsListenerContainerFactory` automatically.

With this single bean, you can inject `JmsTemplate` and use `@JmsListener` without any further configuration. Spring Boot's `spring.jms.*` properties apply as usual:

```properties
# application.properties
spring.jms.pub-sub-domain=false
spring.jms.listener.concurrency=3
spring.jms.listener.max-concurrency=10
```

#### JNDI Lookup in Application Servers

When running inside an application server that exposes `RedissonConnectionFactory` through JNDI, Spring Boot can look it up directly. Configure the JNDI name in `application.properties`:

```properties
spring.jms.jndi-name=java:/jms/RedissonConnectionFactory
```

Spring Boot will retrieve the `ConnectionFactory` from JNDI and use it for `JmsTemplate` and `@JmsListener` processing. No explicit bean definition is needed.

For non-Boot Spring applications, use `JndiObjectFactoryBean`:

```java
@Bean
public JndiObjectFactoryBean connectionFactory() {
    JndiObjectFactoryBean bean = new JndiObjectFactoryBean();
    bean.setJndiName("java:/jms/RedissonConnectionFactory");
    return bean;
}
```


### JNDI

JNDI provides a declarative, configuration-driven approach. The `RedissonInitialContextFactory` reads environment properties, creates connection factories, queues, and topics, and binds them into a read-only JNDI context.

#### Basic Setup

```java
Hashtable<Object, Object> env = new Hashtable<>();
env.put(Context.INITIAL_CONTEXT_FACTORY,
        "org.redisson.jms.jndi.RedissonInitialContextFactory");

// Connection factory
env.put("connectionfactory.myFactory.singleServerConfig.address",
        "redis://127.0.0.1:6379");
env.put("connectionfactory.myFactory.registrationKey", "${REDISSON_KEY}");
env.put("connectionfactory.myFactory.clientId", "myClient");

// Destinations
env.put("queue.myQueue.name", "orders");
env.put("topic.myTopic.name", "notifications");

Context ctx = new InitialContext(env);

ConnectionFactory cf = (ConnectionFactory) ctx.lookup("myFactory");
Queue queue = (Queue) ctx.lookup("myQueue");
Topic topic = (Topic) ctx.lookup("myTopic");
```

The JNDI context is read-only. Attempts to call `bind()`, `rebind()`, `unbind()`, `rename()`, `createSubcontext()`, or `destroySubcontext()` throw `OperationNotSupportedException`.

#### Context Lifecycle

When the JNDI context is closed, all Redisson clients created during initialization are shut down automatically. This releases Redis connections and background threads. Connection factories obtained from the context should not be used after the context is closed.

```java
Context ctx = new InitialContext(env);
try {
    ConnectionFactory cf = (ConnectionFactory) ctx.lookup("myFactory");
    // ... use cf ...
} finally {
    ctx.close();  // shuts down Redisson clients
}
```


### JNDI Environment Properties

All JNDI configuration is expressed as flat key-value properties in the JNDI environment `Hashtable`, in a `PROVIDER_URL` properties file, or in a `jndi.properties` file on the classpath.

#### Connection Factory Properties

Connection factory properties follow the pattern `connectionfactory.<n>.<property>`.

The `<n>` segment is the JNDI lookup name for the factory. Any key matching `connectionfactory.<n>.*` (three or more dot-separated segments) causes a factory with that name to be created. No separate declaration is needed — the presence of properties implies the factory.

| Property | Description |
|----------|-------------|
| `connectionfactory.<n>.configFile` | Path or URL to a Redisson YAML configuration file. When present, inline Redisson properties are ignored for this factory. Supports variable expansion. |
| `connectionfactory.<n>.clientId` | JMS client identifier assigned to all connections from this factory. Required for durable subscriptions. Supports variable expansion. |
| `connectionfactory.<n>.<redissonProperty>` | Any Redisson configuration property in camelCase, matching YAML field names exactly. Passed through Redisson's `PropertiesConvertor` and parsed as YAML. |

Redisson properties use camelCase names that correspond directly to the field names in Redisson's YAML configuration format. Nested objects are expressed with dot notation.

Example of inline Redisson configuration:

```properties
connectionfactory.myFactory.singleServerConfig.address=redis://myhost:6379
connectionfactory.myFactory.singleServerConfig.timeout=5000
connectionfactory.myFactory.singleServerConfig.connectionMinimumIdleSize=10
connectionfactory.myFactory.threads=16
connectionfactory.myFactory.nettyThreads=32
connectionfactory.myFactory.registrationKey=${REDISSON_KEY}
connectionfactory.myFactory.clientId=myClient
```

Example using an external YAML file instead:

```properties
connectionfactory.myFactory.configFile=/etc/redisson/config.yaml
connectionfactory.myFactory.clientId=myClient
```

The connection factory prefix is case-insensitive (`ConnectionFactory.myFactory.*` also works), but the property names after the factory name are case-sensitive and must match Redisson's YAML field names exactly.

Multiple factories can be defined in the same environment:

```properties
connectionfactory.primary.singleServerConfig.address=redis://primary:6379
connectionfactory.primary.registrationKey=${REDISSON_KEY}
connectionfactory.secondary.singleServerConfig.address=redis://secondary:6379
connectionfactory.secondary.registrationKey=${REDISSON_KEY}
```

#### Queue Properties

Queue properties follow the pattern `queue.<n>.<property>`.

The `<n>` segment is the JNDI lookup name. The `name` property (the physical Redis queue name) is required. Additional properties configure the queue behavior and are applied to all connection factories in the context.

| Property | Description |
|----------|-------------|
| `queue.<n>.name` | Physical Redis queue name (required). The JNDI name `<n>` is the lookup key; this value is the underlying queue name in Redis. Supports variable expansion. |
| `queue.<n>.<property>` | Queue configuration property. Maps to a method on Redisson's `QueueConfigParams`. See [Queue Configuration Properties](#queue-configuration-properties). |

Example:

```properties
queue.orders.name=physicalOrders
queue.orders.deliveryLimit=5
queue.orders.visibility=60000

queue.deadLetters.name=physicalDeadLetters
```

The old shorthand form `queue.myQueue=physicalName` (two segments, no `.name`) is not supported. Only the explicit form with a `.name` property creates a binding.

#### Topic Properties

Topic properties follow the pattern `topic.<n>.<property>`.

| Property | Description |
|----------|-------------|
| `topic.<n>.name` | Physical Redis topic name (required). Supports variable expansion. |
| `topic.<n>.<property>` | Topic configuration property. Maps to a method on Redisson's `TopicConfigParams`. See [Topic Configuration Properties](#topic-configuration-properties). |

#### Subscription Properties

Subscription properties are nested under their parent topic: `topic.<n>.subscription.<subName>.<property>`.

| Property | Description |
|----------|-------------|
| `topic.<n>.subscription.<s>.<property>` | Configuration property for subscription `<s>` on topic `<n>`. Maps to a method on Redisson's `SubscriptionConfigParams`. See [Subscription Configuration Properties](#subscription-configuration-properties). |

Subscription properties require the parent topic to have a `name` property. If subscription properties are present without a topic name, a `NamingException` is thrown.

Example with topics and subscriptions:

```properties
topic.events.name=physicalEvents
topic.events.subscription.mySub.deliveryLimit=3
topic.events.subscription.mySub.deadLetterTopicName=physicalEvents-dlt
topic.events.subscription.anotherSub.deliveryLimit=10

topic.alerts.name=physicalAlerts
```

#### Configuration Application

Queue, topic, and subscription configurations are applied to all connection factories in the context. This is because destinations are shared resources — multiple factories pointing at the same Redis instance share the same physical queues and topics.

If no connection factories are defined (only queues and topics), configurations are parsed but not applied, and no error is thrown.

#### PROVIDER_URL

The `java.naming.provider.url` property specifies a path or URL to a `.properties` file containing additional environment properties. Properties from this file are merged into the environment, with the directly-specified environment entries taking precedence.

```java
env.put(Context.PROVIDER_URL, "/etc/redisson-jms/jndi.properties");
```

Accepted formats: a filesystem path (`/etc/config.properties`), a `file:` URL (`file:///etc/config.properties`), or an `http:`/`https:` URL. Windows-style `file://` URLs with drive letters are handled correctly.

If the `PROVIDER_URL` is not set in the environment, the factory checks the `java.naming.provider.url` system property as a fallback.

#### Dynamic Destinations

Queues and topics can be looked up dynamically without pre-declaring them in the environment. The context exposes two sub-contexts for this purpose:

```java
Queue queue = (Queue) ctx.lookup("dynamicQueues/myQueueName");
Topic topic = (Topic) ctx.lookup("dynamicTopics/myTopicName");
```

Dynamic destinations are created on first lookup with the lookup name as the physical destination name. They do not have configuration applied — they use the factory defaults.

#### Variable Expansion

All string values in the JNDI environment support `${variable}` syntax. Variables are resolved in this order:

1. Java system properties (`System.getProperty`)
2. OS environment variables (`System.getenv`)
3. The JNDI environment map itself

If a variable cannot be resolved, an `IllegalArgumentException` is thrown. To provide a fallback, use the `${variable:-default}` syntax:

```properties
connectionfactory.myFactory.singleServerConfig.address=${REDIS_URL:-redis://localhost:6379}
queue.orders.name=${ORDER_QUEUE:-orders}
```

Variable expansion applies to `clientId`, `configFile`, queue names, topic names, and destination config property values. Inline Redisson properties (those passed through `PropertiesConvertor`) are not expanded by the JNDI layer — Redisson's own YAML parser handles `${variable}` resolution from system properties and environment variables natively.

#### Property Value Type Conversion

Destination configuration property values are strings in the environment but are automatically converted to the type expected by the config class method:

| Target Type | Format | Example |
|-------------|--------|---------|
| `String` | Plain text | `myValue` |
| `int` / `Integer` | Decimal integer | `42` |
| `long` / `Long` | Decimal integer | `9999999999` |
| `boolean` / `Boolean` | `true` or `false` | `true` |
| `double` / `Double` | Decimal number | `3.14` |
| `float` / `Float` | Decimal number | `1.5` |
| `Duration` | Milliseconds (numeric) or ISO-8601 | `5000` or `PT30S` |

For `Duration`, a plain numeric value is interpreted as milliseconds. Non-numeric values are parsed as ISO-8601 durations (e.g. `PT5M` for five minutes, `PT30S` for thirty seconds).

#### Method Resolution

Destination configuration properties are applied reflectively. For a property named `deliveryLimit`, the factory first looks for a JavaBean setter `setDeliveryLimit(int)`. If not found, it tries a fluent method `deliveryLimit(int)`. This accommodates both traditional setter-based APIs and Redisson PRO's fluent builder-style config classes.

If neither method is found on the config class, an `IllegalArgumentException` is thrown, wrapped in a `NamingException`.


### Destination Configuration Properties

This section documents all available properties for queue, topic, and subscription configuration. These properties apply to both the native Java API (via fluent builder methods on the config objects) and JNDI (via flat key-value properties in the environment).

In the native API, properties are set using fluent methods on the config objects (`QueueConfig.defaults().deliveryLimit(5)`). In JNDI, the same property names are used as the final segment of the key (`queue.orders.deliveryLimit=5`).

#### Queue Configuration Properties

Queue configuration controls the behavior of `RReliableQueue` instances. In the native API, use `QueueConfig.defaults()` to create a config object; in JNDI, use `queue.<n>.<property>`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `deliveryLimit` | `int` | `10` | Maximum number of delivery attempts for a message. Once reached, the message is moved to the dead letter queue if configured, otherwise deleted. Can be overridden per-message. |
| `visibility` | `Duration` | `30s` | Duration a message is invisible to other consumers after being polled. Prevents duplicate processing. If processing fails or crashes, the message reappears after this timeout. Can be overridden per-poll. |
| `timeToLive` | `Duration` | `0` | Time-to-live for messages. Messages are automatically removed after this duration. `0` means no expiration. Can be overridden per-message. |
| `deadLetterQueueName` | `String` | `null` | Name of the dead letter queue for messages that reached the delivery limit or were rejected. The dead letter queue is a separate `RReliableQueue` instance. `null` disables dead-lettering. |
| `maxMessageSize` | `int` | `0` | Maximum allowed size in bytes for a single message. Messages exceeding this size are rejected. `0` means no limit. |
| `delay` | `Duration` | `0` | Delay before a message becomes available for consumption after being added. `0` means immediate availability. Can be overridden per-message. |
| `maxSize` | `int` | `0` | Maximum number of messages that can be stored in the queue. When reached, add operations may block or return empty. `0` means no limit. |
| `processingMode` | `String` | `PARALLEL` | How messages are processed by consumers. `SEQUENTIAL` enforces one message at a time in strict order. `PARALLEL` allows concurrent consumption by multiple consumers. |

Native API example:

```java
QueueConfig queueConfig = QueueConfig.defaults()
    .deliveryLimit(5)
    .visibility(Duration.ofSeconds(60))
    .timeToLive(Duration.ofHours(24))
    .deadLetterQueueName("orders-dlq")
    .maxSize(10000)
    .processingMode(ProcessingMode.SEQUENTIAL);

cf.setQueueConfig("orders", queueConfig);
```

JNDI example:

```properties
queue.orders.name=orders
queue.orders.deliveryLimit=5
queue.orders.visibility=60000
queue.orders.timeToLive=PT24H
queue.orders.deadLetterQueueName=orders-dlq
queue.orders.maxSize=10000
queue.orders.processingMode=SEQUENTIAL
```

#### Topic Configuration Properties

Topic configuration controls the behavior of `RReliablePubSubTopic` instances. In the native API, use `TopicConfig.defaults()` to create a config object; in JNDI, use `topic.<n>.<property>`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `deliveryLimit` | `int` | `10` | Maximum delivery attempts for a message. Once reached, the message is moved to the dead letter topic if configured, otherwise deleted. Can be overridden per-subscription or per-message. |
| `visibility` | `Duration` | `30s` | Duration a message is invisible to other consumers after being polled. Can be overridden per-subscription, per-poll, or per-push-listener. |
| `timeToLive` | `Duration` | `0` | Time-to-live for messages. Messages are automatically removed after this duration. `0` means no expiration. Can be overridden per-message. |
| `maxMessageSize` | `int` | `0` | Maximum allowed size in bytes for a single message. `0` means no limit. |
| `delay` | `Duration` | `0` | Delay before a message becomes available for consumption. `0` means immediate availability. Can be overridden per-message. |
| `maxSize` | `int` | `0` | Maximum number of messages stored in the topic. When reached, publish operations may block or return empty. `0` means no limit. |
| `retentionMode` | `String` | `SUBSCRIPTION_OPTIONAL_RETAIN_ALL` | Controls message retention behavior. `SUBSCRIPTION_REQUIRED_DELETE_PROCESSED` — requires at least one subscriber; messages are discarded when all subscriptions have processed them. `SUBSCRIPTION_REQUIRED_RETAIN_ALL` — requires at least one subscriber; messages are never discarded. `SUBSCRIPTION_OPTIONAL_RETAIN_ALL` — subscribers not required; messages are always stored. |

Native API example:

```java
TopicConfig topicConfig = TopicConfig.defaults()
    .deliveryLimit(10)
    .visibility(Duration.ofMinutes(2))
    .timeToLive(Duration.ofDays(7))
    .maxSize(50000)
    .retentionMode(RetentionMode.SUBSCRIPTION_REQUIRED_DELETE_PROCESSED);

cf.setTopicConfig("events", topicConfig);
```

JNDI example:

```properties
topic.events.name=events
topic.events.deliveryLimit=10
topic.events.visibility=120000
topic.events.timeToLive=PT168H
topic.events.maxSize=50000
topic.events.retentionMode=SUBSCRIPTION_REQUIRED_DELETE_PROCESSED
```

#### Subscription Configuration Properties

Subscription configuration controls individual subscriptions on a topic. Each subscription maintains an independent offset and tracks message consumption separately. In the native API, use `SubscriptionConfig.name("...")` to create a config object; in JNDI, use `topic.<n>.subscription.<s>.<property>`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `deliveryLimit` | `int` | `10` | Maximum delivery attempts for a message within this subscription. Overrides the topic-level `deliveryLimit`. Once reached, the message is moved to the dead letter topic if configured. |
| `visibility` | `Duration` | `30s` | Duration a message is invisible after being polled by a consumer in this subscription. Overrides the topic-level `visibility`. |
| `deadLetterTopicName` | `String` | `null` | Name of the dead letter topic for this subscription. Messages that reached the delivery limit or were rejected are sent here. The dead letter topic is a separate `RReliablePubSubTopic` instance. `null` disables dead-lettering. |
| `retainAfterAck` | `boolean` | `false` | When `true`, messages are retained in the subscription after acknowledgment instead of being discarded. |

Native API example:

```java
Subscription<MyObject> sub = topic.createSubscription(
    SubscriptionConfig.name("auditLog")
        .deliveryLimit(20)
        .visibility(Duration.ofMinutes(5))
        .deadLetterTopicName("events-dlt")
        .position(Position.earliest()));

cf.setSubscriptionConfig("auditLog", sub.getConfig());
```

JNDI example:

```properties
topic.events.name=events
topic.events.subscription.auditLog.deliveryLimit=20
topic.events.subscription.auditLog.visibility=300000
topic.events.subscription.auditLog.deadLetterTopicName=events-dlt
topic.events.subscription.auditLog.retainAfterAck=true
```


### Complete JNDI Example

```properties
# jndi.properties

java.naming.factory.initial=org.redisson.jms.jndi.RedissonInitialContextFactory

# Primary factory — inline config
connectionfactory.primary.singleServerConfig.address=redis://${REDIS_HOST:-localhost}:6379
connectionfactory.primary.singleServerConfig.timeout=5000
connectionfactory.primary.threads=16
connectionfactory.primary.registrationKey=${REDISSON_KEY}
connectionfactory.primary.clientId=app-primary

# Secondary factory — YAML file
connectionfactory.secondary.configFile=${REDISSON_CONFIG_PATH}
connectionfactory.secondary.clientId=app-secondary

# Queues
queue.orders.name=app.orders
queue.orders.deliveryLimit=5
queue.orders.visibility=60000
queue.orders.deadLetterQueueName=app.orders-dlq

queue.deadLetters.name=app.orders-dlq

# Topics with subscriptions
topic.events.name=app.events
topic.events.deliveryLimit=10
topic.events.retentionMode=SUBSCRIPTION_REQUIRED_DELETE_PROCESSED
topic.events.subscription.auditLog.deliveryLimit=20
topic.events.subscription.auditLog.deadLetterTopicName=app.events-dlt
topic.events.subscription.analytics.deliveryLimit=3

topic.alerts.name=app.alerts
topic.alerts.visibility=120000
```

```java
Context ctx = new InitialContext();  // reads jndi.properties from classpath

try {
    ConnectionFactory primary = (ConnectionFactory) ctx.lookup("primary");
    Queue orders = (Queue) ctx.lookup("orders");
    Topic events = (Topic) ctx.lookup("events");

    try (JMSContext jms = primary.createContext()) {
        jms.createProducer().send(orders, "new order");
        jms.createProducer().send(events, "order created");
    }
} finally {
    ctx.close();
}
```
