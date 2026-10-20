## Spring AI Chat Memory

*This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition.*

*Requires Spring AI 2.0.x.*

Redisson provides a [Spring AI](https://spring.io/projects/spring-ai) `ChatMemoryRepository` implementation, which stores the messages of a conversation so that a model can be given the history of an exchange rather than a single turn.

Each message is stored as a Redis JSON document and indexed by the Redis Query Engine, so a conversation can be read back in order, and messages can be searched by content, type, timestamp or metadata across every conversation.

**Features:**

- Full `ChatMemoryRepository` implementation, usable with `MessageChatMemoryAdvisor` and the rest of Spring AI's chat memory support
- Every Spring AI message type: user, assistant, system and tool response
- Tool calls, tool responses and attached media preserved across a round trip
- Per-message time to live
- Search by content, message type, timestamp range or metadata
- Configurable metadata fields (TEXT, TAG, NUMERIC) for filtering
- Strictly increasing message ordering within a conversation, maintained across concurrent writers

### Prerequisites

1. **Redis 8.0 or higher**. The Redis Query Engine and JSON are part of Redis itself from Redis 8, so the standard `redis` distribution and Docker image carry everything the repository needs and no module has to be loaded. For Redis 7.x and earlier, use **Redis Stack**.

### Usage

**1. Add dependency into your project**

**Spring Boot Starter** (recommended)

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>redisson-spring-ai-chat-starter-20</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
compile 'pro.redisson:redisson-spring-ai-chat-starter-20:xVERSIONx'
```

**Repository Implementation Only**

For manual configuration or non-Spring Boot applications:

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>redisson-spring-ai-chat-20</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
compile 'pro.redisson:redisson-spring-ai-chat-20:xVERSIONx'
```

[License key configuration](configuration.md/#license-key-configuration)

**2. Use the repository in your application**

The repository is a plain `ChatMemoryRepository`, so it plugs into Spring AI's chat memory the same way any other does:

```java
@Autowired
ChatMemoryRepository chatMemoryRepository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(chatMemoryRepository)
        .maxMessages(20)
        .build();

String answer = chatClient.prompt()
        .user("What did I ask you first?")
        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "user-42"))
        .call()
        .content();
```

Used directly, through the `ChatMemoryRepository` contract:

```java
chatMemoryRepository.saveAll("user-42", List.of(
        new UserMessage("What is Redisson?"),
        new AssistantMessage("A Java client for Valkey and Redis.")));

List<Message> history = chatMemoryRepository.findByConversationId("user-42");

List<String> conversations = chatMemoryRepository.findConversationIds();

chatMemoryRepository.deleteByConversationId("user-42");
```

`RedissonChatMemoryRepository` adds methods of its own, which append to a conversation instead of replacing it:

```java
RedissonChatMemoryRepository repository = new RedissonChatMemoryRepository(options);

// append one message, or several, keeping what is already there
repository.add("user-42", new UserMessage("What is Redisson?"));
repository.add("user-42", List.of(
        new AssistantMessage("A Java client for Valkey and Redis."),
        new UserMessage("Does it do vector search?")));

// read the conversation back in order
List<Message> all = repository.get("user-42");

// or only the most recent messages
List<Message> recent = repository.get("user-42", 10);

// remove one conversation
repository.clear("user-42");
```

`saveAll` replaces a conversation, so appending a turn through it means re-sending the whole history. `add` is the one to call on a chat path.

### Configuration

The repository is configured through `RedissonChatMemoryOptions`:

```java
@Bean
ChatMemoryRepository chatMemoryRepository(RedissonClient redisson) {
    return new RedissonChatMemoryRepository(
            RedissonChatMemoryOptions.client(redisson)
                    .indexName("chat-memory-idx")
                    .keyPrefix("chat-memory:")
                    .timeToLive(Duration.ofDays(30))
                    .initializeSchema(true)
                    .maxConversationIds(1000)
                    .maxMessagesPerConversation(1000)
                    .metadataFields(List.of(
                            Map.of("name", "priority", "type", "tag"),
                            Map.of("name", "score", "type", "numeric"))));
}
```

| Option | Description | Default Value |
|--------|-------------|---------------|
| `indexName` | Name of the search index holding the messages | `chat-memory-idx` |
| `keyPrefix` | Prefix prepended to the key of every stored message | `chat-memory:` |
| `timeToLive` | How long a stored message lives, counted from when it is written. A negative duration keeps messages forever | no expiration |
| `initializeSchema` | Whether the index is created on startup if it is absent | `true` |
| `maxConversationIds` | Largest number of conversation ids `findConversationIds()` returns | `1000` |
| `maxMessagesPerConversation` | Largest number of messages returned for one conversation | `1000` |
| `metadataFields` | Metadata fields added to the index, which makes them available for filtering | empty |

**Metadata fields**

Message metadata is always stored and always returned, but only the fields declared here can be filtered on. Each entry is a map holding a `name` and a `type`, following the RedisVL schema format:

| Type | Description | Use Case |
|------|-------------|----------|
| `tag` | Exact match filtering | Categorical data, labels, status values |
| `text` | Full-text search | Descriptions, free-form content |
| `numeric` | Range queries | Scores, counts, confidence values |

When no metadata fields are declared, all metadata is indexed as one full-text field instead, which supports loose matching but no typed filtering.

### Message ordering

Messages within a conversation are ordered by a timestamp reserved from a Redis counter rather than taken from the local clock, so ordering holds when several application instances write to the same conversation at once. A batch reserves a block of timestamps in one atomic step, so messages saved together keep the order they were passed in.

### Searching across conversations

Beyond the `ChatMemoryRepository` contract, the repository can search the whole index. Every method returns the message together with the conversation it belongs to and the timestamp it was stored at:

```java
AdvancedRedissonChatMemoryRepository advanced = (AdvancedRedissonChatMemoryRepository) chatMemoryRepository;

// each result is a record carrying the message, its conversation and its timestamp
for (AdvancedRedissonChatMemoryRepository.MessageWithConversation result : advanced.findByType(MessageType.USER, 10)) {
    result.message();
    result.conversationId();
    result.timestamp();
}

// by content, using the query engine's own text syntax, so wildcards work
advanced.findByContent("deploy*", 10);

// by message type
advanced.findByType(MessageType.ASSISTANT, 10);

// by timestamp range, optionally scoped to one conversation
advanced.findByTimeRange("user-42", Instant.now().minus(Duration.ofDays(1)), Instant.now(), 50);

// by a declared metadata field
advanced.findByMetadata("priority", "high", 10);

// or with a raw query
advanced.executeQuery("@type:(ASSISTANT) @conversation_id:{user\\-42}", 10);
```

### Stored document layout

Each message is stored as a JSON document under `<keyPrefix><conversation id>:<timestamp>`:

| Field | Holds |
|-------|-------|
| `type` | the message type, indexed as `TEXT` |
| `content` | the message text, indexed as `TEXT` |
| `conversation_id` | the conversation, indexed as `TAG` |
| `timestamp` | when the message was stored, indexed as sortable `NUMERIC` |
| `metadata` | the message metadata |
| `toolCalls` | the tool calls of an assistant message, when it has any |
| `toolResponses` | the responses of a tool message, when it has any |
| `media` | attached media, with binary content Base64 encoded |
