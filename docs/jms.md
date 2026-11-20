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

// Synchronization (replication) configuration — default and per-destination
cf.setSyncConfig(myDefaultSyncConfig);
cf.setQueueSyncConfig("orders", strictSync);
cf.setTopicSyncConfig("events", strictSync);

// Delay before a failed message is redelivered (rollback, recover, listener exception)
cf.setRedeliveryDelay(Duration.ofSeconds(5));

// Visibility renewal for topic consumers (enabled by default)
cf.setVisibilityRenewal(true);
```

Per-destination configuration takes precedence over the default. If neither is set for a given destination, the factory returns `null` and Redisson uses its own built-in defaults.

The available properties for each configuration type are listed in the [Destination Configuration Properties](#destination-configuration-properties) section. Synchronization semantics specific to JMS — async sends, transacted sessions, `CLIENT_ACKNOWLEDGE` — are covered in [Synchronization Configuration](#synchronization-configuration). Transactions, visibility renewal and the redelivery delay are covered in [Transactions and Redelivery](#transactions-and-redelivery).


#### Synchronization Configuration

Synchronization (replication) controls whether JMS write operations block until data has been replicated to Valkey or Redis secondary nodes. It applies uniformly to every JMS operation that causes a state change: sends, acknowledgements, negative acknowledgements, polls (which remove or mark-invisible), topic pulls, and topic visibility renewals.

Three parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `syncMode` | `SyncMode` | `AUTO` | `AUTO` — prefer AOF fsync on primary + replicas if AOF persistence is enabled, otherwise fall back to replica-ack. `ACK` — block until replicas acknowledge the write in memory. `ACK_AOF` — block until AOF has fsynced on primary and replicas (Redis 7.2+ / any Valkey). |
| `syncFailureMode` | `SyncFailureMode` | `LOG_WARNING` | `LOG_WARNING` — if the sync target is not met within `syncTimeout`, log and continue; the operation still succeeded on the primary. `THROW_EXCEPTION` — raise the failure to the caller as a `JMSException`. |
| `syncTimeout` | `Duration` | `1 second` | How long to wait for replica acknowledgement before invoking `syncFailureMode`. |

Resolution is **replace, not merge**: per-destination config fully replaces the factory default rather than layering on top. If a factory sets `syncMode(ACK)` as the default and a queue sets `syncTimeout(500ms)` as its override, the queue operations use `(AUTO, LOG_WARNING, 500ms)` — all unset fields on the per-destination config fall back to `JmsSyncConfig.defaults()`, not to the factory default. To keep one field and change another, construct the per-destination config explicitly with every value you want.

```java
JmsSyncConfig strict = JmsSyncConfig.defaults()
    .syncMode(SyncMode.ACK)
    .syncFailureMode(SyncFailureMode.THROW_EXCEPTION)
    .syncTimeout(Duration.ofMillis(500));

cf.setSyncConfig(strict);                 // global default
cf.setQueueSyncConfig("audit", strict);   // per-queue override
cf.setTopicSyncConfig("events", strict);  // per-topic override
```

##### JMS-specific surfacing

When `syncFailureMode` is `THROW_EXCEPTION`, replication failures reach the application differently depending on the operation:

- **Synchronous `send`** → `JMSException` thrown from `producer.send(...)` / `context.createProducer().send(...)`.
- **Asynchronous `send`** (JMS 2.0+ `setAsync` with a `CompletionListener`) → `CompletionListener.onException(message, exception)` fires. Applications that do not register a `CompletionListener` will not observe the failure. Register one if you use strict sync with async send.
- **`Session.commit()`** (transacted session) → a replication failure on a send rolls the transaction back and throws `TransactionRolledBackException`; the failing send itself may already be published on the primary. A replication failure on an acknowledgement throws `JMSException`, and the messages whose acknowledgement failed stay in the transaction. See [Transactions and Redelivery](#transactions-and-redelivery).
- **`Message.acknowledge()`** under `CLIENT_ACKNOWLEDGE` / `DUPS_OK_ACKNOWLEDGE` → `JMSException` thrown. A single `acknowledge()` call acks all previously delivered messages on the session; under `THROW_EXCEPTION` a mid-batch replication failure aborts the remaining acks.

The pragmatic default for ack paths in batching ack modes is `LOG_WARNING`. Use `THROW_EXCEPTION` on ack only when the application can handle partial completion.

##### Topic subscriptions

Sync config is resolved by **topic name**. Acknowledgements and negative acknowledgements from any subscription on a topic inherit the topic's sync config. There is no per-subscription sync config — if an application needs one durable subscriber to use strict sync while others use defaults, split it onto a separate topic.

##### Non-durable / temporary destinations

Temporary queues and topics generate UUID-based physical names that are unlikely to match any configured `setQueueSyncConfig` / `setTopicSyncConfig` entry. They fall through to the factory default (if any) and then to Redisson defaults.


#### Transactions and Redelivery

In a transacted session, sends are held in the client until `commit()`, and consumed messages stay unacknowledged until then. A send still sets `JMSMessageID` when `send()` returns (see [Message IDs](#message-ids)), so it can be used, for example as a correlation ID, before the message is published.

Closing a consumer does not take its messages out of the transaction: commit acknowledges them, and rollback, or closing the session, returns them. Until then a topic consumer keeps renewing their visibility, and its subscription stays in place. Spring relies on this: it closes consumers before it commits, both under a JTA transaction manager and in `JmsTemplate.receive()` called from a listener.

`commit()` runs three steps in this order:

1. **Check.** If the transaction has sends, each topic consumer confirms that it still holds every message it consumed and extends their visibility for the rest of the commit. This needs [visibility renewal](#visibility-renewal), which is enabled by default.
2. **Send.** The held sends are published in order, stopping at the first failure.
3. **Acknowledge.** The consumed messages are acknowledged.

What a failure does:

| Failure during `commit()` | Effect | `commit()` throws |
|---------------------------|--------|-------------------|
| A consumed topic message is no longer held: its visibility expired and it may have been redelivered. Checked only when the transaction has sends | The transaction is rolled back before anything is published | `TransactionRolledBackException` |
| A send fails | Sends before it stay published, and so does the failing send if only its replication failed. The remaining sends are dropped, and the consumed messages are redelivered, after the [redelivery delay](#redelivery-delay) if one is set | `TransactionRolledBackException` |
| An acknowledgement fails | The sends are published. Messages whose acknowledgement failed stay in the transaction | `JMSException`. Call `commit()` again to finish, or `rollback()` to redeliver them |
| A topic acknowledgement is not applied, because the message was delivered again after its visibility expired | A warning is logged | Nothing |

A commit is not atomic as it is in a JMS broker. If it fails after some sends are published, or the process stops during the commit, those sends stay published and the consumed messages are redelivered, so the processing that produced the sends runs again. Delivery is at-least-once: consumers of messages sent inside transactions should tolerate duplicates.

##### Visibility renewal

A consumed message is hidden from other consumers only for its visibility timeout (30 seconds unless configured). If processing takes longer, the message is delivered to another consumer while the first one still works on it.

Topic consumers prevent this by renewing visibility. A topic consumer pulls each message with a known visibility, the lease, and renews it each time a third of the lease has passed since the last extension, for as long as it holds the message: while `onMessage` runs, until `Message.acknowledge()` in `CLIENT_ACKNOWLEDGE` mode, and until commit or rollback in a transacted session. The timeout then takes effect only when the consumer stops renewing, for example after a crash.

Renewal starts when the message is handed to the application: when `receive()` returns it, or when its `onMessage` call starts. If several consumers with listeners share one session, a message pulled for one of them can wait for the session's single listener thread while another consumer's `onMessage` runs, and it is not renewed during that wait.

The lease is the visibility set on the factory for the subscription (`setSubscriptionConfig`), else for the topic (`setTopicConfig`), else 30 seconds. Pulls set it explicitly, so it replaces any visibility configured on the subscription or topic outside the factory.

Renewal is enabled by default. To turn it off, together with the check at commit:

```java
cf.setVisibilityRenewal(false);
```

The trade-off is the same as with a JMS broker: a message held by a consumer that is alive but stuck is not redelivered.

Topic acknowledgements, rollbacks, renewals and the commit check carry the delivery number of the message they refer to. If the message has been delivered again since, the server ignores them. A consumer that lost a message therefore cannot acknowledge it, or return it, while another consumer processes it.

Queue consumers do not renew, because `RReliableQueue` cannot extend the visibility of a polled message. Queue acknowledgements also carry no delivery number: if a queue message's visibility expires and another consumer receives it, a late acknowledgement from the first consumer removes it. Set the queue's visibility above the longest processing time, including the time a transaction stays open:

```java
cf.setQueueConfig("orders", QueueConfig.defaults().visibility(Duration.ofMinutes(5)));
```

##### Redelivery delay

By default, a message that fails is redelivered immediately, so a message that always fails is retried in a tight loop until it reaches its delivery limit. A redelivery delay holds it back first:

```java
cf.setRedeliveryDelay(Duration.ofSeconds(5));
```

The delay applies after `Session.rollback()`, closing a transacted session (which rolls its transaction back), `Session.recover()`, a commit that is rolled back, and a `MessageListener` that throws in `AUTO_ACKNOWLEDGE` or `DUPS_OK_ACKNOWLEDGE` mode. Messages returned because a consumer or a non-transacted session closes are not delayed, so other consumers can take them at once. Configure a `deliveryLimit` and a dead letter queue or topic as well, so a message that always fails ends up there.


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

##### Message IDs

The provider chooses each message's ID before sending it: a random 128-bit value written as 32 hex digits, the same form Redisson generates. `JMSMessageID` is therefore set when `send()` returns, also in a transacted session, where the message is published only at commit, and for an asynchronous send, before the `CompletionListener` is called. The consumer receives the same `JMSMessageID`, and a message selector on `JMSMessageID` matches it.

Redisson resends a command whose reply does not arrive in time. The resend carries the same ID, so it is not added a second time while the first copy is still in the queue or topic.

A send to a queue or topic that is full (its `maxSize` is reached) throws `ResourceAllocationException`, or `ResourceAllocationRuntimeException` from a `JMSContext`. An asynchronous send reports it to the `CompletionListener`, and in a transacted session `commit()` rolls back. Redisson reports a full destination and an ID that is already taken the same way, so the provider tells them apart by checking whether a message with that ID exists. One case is misreported: a resend whose first attempt went through and whose message was consumed and removed before the check. The send then fails although the message was delivered, and retrying it delivers the message twice.

With `setDisableMessageID(true)`, `JMSMessageID` stays `null`, but the message is still sent with an ID.

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

The factory maintains a ref-counted internal scheduler for async delivery retry and visibility renewal. The scheduler starts when the first connection opens and shuts down when the last connection closes. No explicit cleanup of the factory itself is required.


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

#### Transactions

Spring Boot's auto-configured listener container uses transacted sessions, so each `@JmsListener` invocation runs in a local transaction. Messages the listener sends through `JmsTemplate`, or returns with `@SendTo`, are published at commit, followed by the acknowledgement of the received message. A listener that throws rolls the transaction back, and the received message is redelivered after the [redelivery delay](#redelivery-delay). What happens when a commit fails is described in [Transactions and Redelivery](#transactions-and-redelivery).

A listener on a queue, Spring's default destination type, is not covered by [visibility renewal](#visibility-renewal). Set the queue's visibility above the longest listener run, or the message is delivered to a second listener while the first still works on it.

When a JTA transaction manager is configured, Spring Boot uses it instead of transacted sessions. `RedissonConnectionFactory` does not support XA, so the listener's session then acknowledges each message on receipt, before the listener runs, and a message whose listener throws is lost. Keep a local JMS transaction in that case. With Spring Boot 3.2 or later, set a property:

```properties
spring.jms.listener.session.transacted=true
```

Or configure the container factory:

```java
@Bean
public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
        ConnectionFactory connectionFactory,
        DefaultJmsListenerContainerFactoryConfigurer configurer) {
    DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setSessionTransacted(true);
    return factory;
}
```

Spring then runs a local JMS transaction synchronized with the JTA transaction and commits it after the JTA commit. It creates a consumer for each message and closes it before that commit; the message stays in the transaction (see [Transactions and Redelivery](#transactions-and-redelivery)). If the listener throws, Spring rolls the JTA transaction back and closes the session, which rolls back the JMS transaction, and the message is redelivered after the [redelivery delay](#redelivery-delay). The two commits are not atomic: if the process stops between them, the message is redelivered and processed again.

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
| `connectionfactory.<n>.sync.<property>` | Factory-wide sync configuration. See [Sync Configuration Properties](#sync-configuration-properties). |
| `connectionfactory.<n>.redeliveryDelay` | Delay before a failed message is redelivered, in milliseconds or as an ISO-8601 duration (`PT5S`). See [Redelivery delay](#redelivery-delay). Supports variable expansion. |
| `connectionfactory.<n>.visibilityRenewal` | `false` turns off visibility renewal for topic consumers and the check at commit. Default `true`. See [Visibility renewal](#visibility-renewal). |

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
| `queue.<n>.sync.<property>` | Per-queue sync configuration. Overrides the factory default for this queue. See [Sync Configuration Properties](#sync-configuration-properties). |

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
| `topic.<n>.sync.<property>` | Per-topic sync configuration. Overrides the factory default for this topic and applies to publish, ack, and nack on every subscription of the topic. See [Sync Configuration Properties](#sync-configuration-properties). |

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

#### Sync Configuration Properties

Sync configuration controls replication behaviour for JMS operations. It is applied factory-wide or per-destination through either the native Java API on `RedissonConnectionFactory` or through the JNDI environment using a `sync.` sub-key under an existing `connectionfactory.`, `queue.`, or `topic.` prefix.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `syncMode` | `SyncMode` | `AUTO` | Replication mode: `AUTO`, `ACK`, or `ACK_AOF`. See [Synchronization Configuration](#synchronization-configuration) for semantics. |
| `syncFailureMode` | `SyncFailureMode` | `LOG_WARNING` | Behaviour when the sync target is not met within `syncTimeout`: `LOG_WARNING` or `THROW_EXCEPTION`. |
| `syncTimeout` | `Duration` | `1s` | How long to wait for replica acknowledgement before invoking `syncFailureMode`. |

Native API example:

```java
JmsSyncConfig syncConfig = JmsSyncConfig.defaults()
    .syncMode(SyncMode.ACK)
    .syncFailureMode(SyncFailureMode.THROW_EXCEPTION)
    .syncTimeout(Duration.ofMillis(500));

cf.setSyncConfig(syncConfig);               // factory-wide default
cf.setQueueSyncConfig("audit", syncConfig); // per-queue override
cf.setTopicSyncConfig("events", syncConfig);// per-topic override
```

`setQueueSyncConfig(name, config)` and `setTopicSyncConfig(name, config)` require both arguments to be non-`null` — passing `null` throws `NullPointerException`. To clear the factory-wide default, call `setSyncConfig(null)`. To revert a specific queue or topic to the factory default, reassign it with a fresh `JmsSyncConfig.defaults()`.

JNDI example — three scopes, same property names:

```properties
# Factory-wide default
connectionfactory.myFactory.sync.syncMode=ACK
connectionfactory.myFactory.sync.syncFailureMode=THROW_EXCEPTION
connectionfactory.myFactory.sync.syncTimeout=500

# Per-queue override — lives alongside other queue config properties
queue.orders.name=orders
queue.orders.deliveryLimit=5
queue.orders.sync.syncMode=ACK_AOF
queue.orders.sync.syncTimeout=2000

# Per-topic override
topic.events.name=events
topic.events.sync.syncMode=ACK
```

In JNDI, `sync.syncTimeout` accepts either milliseconds as a number or an ISO-8601 duration (e.g. `PT0.5S`), consistent with every other `Duration` property in the JNDI schema. `syncMode` and `syncFailureMode` accept the enum constant names exactly (`AUTO` / `ACK` / `ACK_AOF`, `LOG_WARNING` / `THROW_EXCEPTION`). Per-subscription sync configuration is not supported — acknowledgements from any subscription inherit the enclosing topic's sync config.


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

# Redelivery delay for failed messages
connectionfactory.primary.redeliveryDelay=PT5S

# Factory-wide default sync config
connectionfactory.primary.sync.syncMode=AUTO
connectionfactory.primary.sync.syncFailureMode=LOG_WARNING

# Queues
queue.orders.name=app.orders
queue.orders.deliveryLimit=5
queue.orders.visibility=60000
queue.orders.deadLetterQueueName=app.orders-dlq
# Stricter sync for the orders queue — override the factory default
queue.orders.sync.syncMode=ACK
queue.orders.sync.syncFailureMode=THROW_EXCEPTION
queue.orders.sync.syncTimeout=500

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
