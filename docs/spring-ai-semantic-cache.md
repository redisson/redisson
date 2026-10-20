## Spring AI Semantic Cache

*This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition.*

*Requires Spring AI 2.0.x.*

A semantic cache answers a request from a previous response when the two questions *mean* the same thing, rather than only when they are spelled the same way. "What is the capital of France?" and "What is France's capital city?" hit the same entry, so the second one costs a vector search instead of a model call.

The cache is built on the [Spring AI Vector Store](spring-ai-vector-store.md): the question is embedded and stored as the document text, and the response travels in the document body.

**Features:**

- Similarity matching through the Redis Query Engine, with a configurable threshold
- Per-lookup threshold override
- Per-entry time to live
- Responses produced under different system prompts kept apart, so changing a prompt does not serve answers produced under the old one
- Eviction of one entry, of one context, or of everything
- A `ChatClient` advisor which caches on the way out and answers on the way in, on both the blocking and the streaming path

### Prerequisites

1. **Redis 8.0 or higher**. The Redis Query Engine and JSON are part of Redis itself from Redis 8, so the standard `redis` distribution and Docker image carry everything the cache needs and no module has to be loaded. For Redis 7.x and earlier, use **Redis Stack**.

2. **EmbeddingModel** instance to compute the query embeddings, as for the vector store.

### Usage

**1. Add dependency into your project**

**Spring Boot Starter** (recommended)

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>redisson-spring-ai-semantic-cache-starter-20</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
compile 'pro.redisson:redisson-spring-ai-semantic-cache-starter-20:xVERSIONx'
```

**Cache Implementation Only**

For manual configuration or non-Spring Boot applications:

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>redisson-spring-ai-semantic-cache-20</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
compile 'pro.redisson:redisson-spring-ai-semantic-cache-20:xVERSIONx'
```

[License key configuration](configuration.md/#license-key-configuration)

**2. Configure the cache**

The cache needs a `RedissonVectorStore` carrying its schema. `RedissonSemanticCache.vectorStoreBuilder` supplies one already configured, leaving the index name and prefix to set:

```java
@Bean
RedissonVectorStore semanticCacheStore(RedissonClient redisson, EmbeddingModel embeddingModel) {
    return RedissonSemanticCache.vectorStoreBuilder(redisson, embeddingModel)
            .indexName("semantic-cache-index")
            .prefix("semantic-cache:")
            .build();
}

@Bean
SemanticCache semanticCache(RedissonVectorStore semanticCacheStore) {
    return RedissonSemanticCache.builder(semanticCacheStore)
            .similarityThreshold(0.9)
            .build();
}
```

`similarityThreshold` is how close a stored question has to be to count as a hit, from 0 to 1, defaulting to `0.8`. Raise it to answer only near-identical questions; lower it to answer more broadly and risk returning a response to a question the user did not ask.

**3. Add the advisor to your ChatClient**

```java
@Bean
SemanticCacheAdvisor semanticCacheAdvisor(SemanticCache cache) {
    return SemanticCacheAdvisor.builder(cache)
            .timeToLive(Duration.ofHours(1))
            .build();
}

String answer = chatClient.prompt()
        .user("What is the capital of France?")
        .advisors(semanticCacheAdvisor)
        .call()
        .content();
```

The advisor looks the question up before the request reaches the model, and caches whatever the model returns when it does not find one. It works the same way on `stream()`, where the response is assembled as it arrives and cached once the stream completes.

| Advisor option | Description | Default Value |
|----------------|-------------|---------------|
| `timeToLive` | How long a cached response stays valid | no expiration |
| `similarityThreshold` | Overrides the cache's own threshold for requests going through this advisor | the cache's threshold |
| `isolateBySystemPrompt` | Whether responses produced under different system prompts are kept apart | `true` |
| `order` | The advisor's place in the chain | `BaseChatMemoryAdvisor` default |
| `scheduler` | The scheduler the streaming path caches on | `Schedulers.boundedElastic()` |

**A cache failure is not a request failure.** If Redis is unreachable the advisor logs it and continues to the model, so an outage makes requests slower rather than failing them.

### Context isolation

A response produced under one system prompt is usually wrong under another - the same question answered "as a pirate" and "as a lawyer" are different answers. The advisor derives a hash from the system prompt and stores the response under it, so only a request carrying the same system prompt can be answered from it.

Requests with no system prompt are stored under no context, and a lookup carrying no context sees only those entries - not everything.

Set `isolateBySystemPrompt(false)` to share responses across system prompts, which is worth doing only when the system prompt has no bearing on the answer.

When a prompt changes, the responses cached under the old one are stale and can be dropped without touching the rest:

```java
cache.evictContext(oldPromptHash);
```

### Using the cache directly

The advisor is a wrapper over an interface which can be used on its own:

```java
// store a response, optionally with a context and a time to live
String id = cache.put(CacheEntry.of("What is the capital of France?", response)
        .withContextHash("assistant-v2")
        .withTimeToLive(Duration.ofHours(1)));

// look one up
Optional<CacheHit> hit = cache.get(CacheQuery.of("What is France's capital city?", "assistant-v2"));

// or with a stricter threshold than the cache's own, for this lookup only
Optional<CacheHit> exact = cache.get(CacheQuery.of("What is the capital of France?")
        .withSimilarityThreshold(0.98));

// remove one entry, one context, or everything
cache.evict(id);
cache.evictContext("assistant-v2");
long removed = cache.clear();
```

A hit reports more than the response:

```java
hit.ifPresent(h -> {
    h.response();      // the cached ChatResponse
    h.query();         // the question it was stored under, which is not the one asked
    h.score();         // how similar the two are, from 0 to 1
    h.id();            // what evict() takes
    h.contextHash();   // the context it was stored under
});
```

`query()` and `score()` are what make a cache hit auditable - whether the threshold is set where it should be, or whether the cache is answering a question the user did not ask. The advisor publishes the whole hit in the response context under `SemanticCacheAdvisor.CACHE_HIT`, so a caller can tell a cached answer from a generated one:

```java
ChatClientResponse response = chatClient.prompt().user(question)
        .advisors(semanticCacheAdvisor)
        .call()
        .chatClientResponse();

CacheHit hit = (CacheHit) response.context().get(SemanticCacheAdvisor.CACHE_HIT);
if (hit != null) {
    logger.info("answered from cache, matched '{}' at {}", hit.query(), hit.score());
}
```

### What is stored

Each entry is one document under `<prefix><entry id>`:

| Field | Holds |
|-------|-------|
| `content` | the question, which is what gets embedded and matched |
| `embedding` | its vector |
| `context_hash` | the context, indexed as `TAG` - the only indexed metadata |
| `response` | the cached response, stored in the document but deliberately **not** indexed |

The response is read back by key once a match is found, rather than being indexed, so a cached response is not tokenized into the search index.

A response is stored as its generations, each carrying the generated text and the metadata of the generated message. Provider-specific response metadata such as token usage and finish reason is **not** preserved, so a cached response is not a byte-for-byte copy of the original.

**Storing replaces rather than accumulates.** Storing a response for a question already answered by a similar enough entry in the same context replaces that entry, so the cache does not fill with near-duplicates of the same question.
