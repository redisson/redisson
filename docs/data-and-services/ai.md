---
description: "Redisson AI: embedding models, an embeddings cache, a vector store over Redis vector sets or the Redis Query Engine, a semantic cache and agent memory for Java, with Spring AI and LangChain4j integrations."
---

## Redisson AI

*This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition.*

<!--
*Redisson AI is experimental. Its API types are annotated `@Experimental`, and the whole library, including extension classes such as `HttpEmbeddingModel`, may change or be removed in any release, including a patch release.*
-->

Redisson AI is a set of Valkey and Redis based building blocks for applications that call large language models and embedding models. It turns text into vectors through any embedding provider, keeps those vectors so the same text is never paid for twice, stores and searches them, answers repeated questions from a cache of earlier answers, and holds an agent's conversation history. Each object is thread-safe and is meant to be created once and shared.

| Object | What it does |
|---|---|
| [Embedding model](#embedding-models) | Turns text into vectors through OpenAI, Ollama, a Spring AI or LangChain4j model, or your own function |
| [Embeddings cache](#embeddings-cache) | Stores computed vectors by text, so a repeated text costs a hash lookup instead of a provider call |
| [Vector store](#vector-store) | Embeds, stores and searches documents over Redis vector sets or the Redis Query Engine, with typed filters and per-tenant scoping |
| [Semantic cache](#semantic-cache) | Returns an earlier model answer when a new prompt means the same thing, and invalidates answers when the documents they were built from change |
| [Agent memory](#agent-memory) | Keeps a bounded, expiring, atomically appended conversation, and archives older turns for semantic recall |
| [Spring AI](#spring-ai-integration) and [LangChain4j](#langchain4j-integration) adapters | Expose the store, the memory and the semantic cache through each framework's own interfaces |

### Installation

Redisson AI is split into modules so an application carries only the provider and framework it uses:

Maven
```xml
<dependencies>
    <dependency>
        <groupId>pro.redisson</groupId>
        <artifactId>redisson-ai-core</artifactId>
        <version>xVERSIONx</version>
    </dependency>
    <dependency>
        <groupId>pro.redisson</groupId>
        <artifactId>redisson-embedding-openai</artifactId>
        <version>xVERSIONx</version>
    </dependency>
</dependencies>
```

Gradle
```groovy
implementation 'pro.redisson:redisson-ai-core:xVERSIONx'
implementation 'pro.redisson:redisson-embedding-openai:xVERSIONx'
```

| Artifact | Contains |
|---|---|
| `redisson-ai-core` | The embedding model interface, embeddings cache, vector store, typed filters, semantic cache and agent memory |
| `redisson-embedding-openai` | OpenAI embedding model |
| `redisson-embedding-ollama` | Ollama embedding model |
| `redisson-embedding-http` | Shared HTTP transport, retry policy, proxy and TLS handling. Pulled in by the two modules above; depend on it directly only to write an adapter for another HTTP provider |
| `redisson-embedding-springai` | Bridge between Spring AI `EmbeddingModel` and Redisson's embedding model, in both directions |
| `redisson-embedding-langchain4j` | Bridge between LangChain4j `EmbeddingModel` and Redisson's embedding model, in both directions |
| `redisson-store-spring-ai` | Spring AI `VectorStore`, `ChatMemoryRepository` and a semantic cache `CallAdvisor` |
| `redisson-store-langchain4j` | LangChain4j `EmbeddingStore`, `ChatMemoryStore` and a caching `ChatModel` |

The Spring AI and LangChain4j modules declare their framework as `provided`, so add the framework artifacts yourself, at a version the adapter supports:

`redisson-store-spring-ai` is compatible with Spring AI 2.x.x
`redisson-embedding-springai` is compatible with Spring AI 1.0.0 - 2.x.x
`redisson-embedding-langchain4j` is compatible with LangChain4j 1.13.x - 1.2x.x

An application can use both Spring AI modules together, on Spring AI 2.0.0. These versions are the compatibility statement rather than a range: a framework upgrade beyond them comes with a new adapter release.

`redisson-ai-core` declares no JSON library. The agent memory always needs a `JsonCodec`, and a vector store needs one whenever it stores documents - which includes the store behind a semantic cache. Use Redisson's `JacksonCodec` with `com.fasterxml.jackson.core:jackson-databind` (already present with `redisson-embedding-openai` or `redisson-embedding-ollama`), `Jackson3Codec` with Jackson 3, or a codec of your own.

### Requirements

**Java 17 or later**.

What each object needs from the server:

| Object | Valkey or Redis requirement |
|---|---|
| Embeddings cache | Redis 4.0+ or any Valkey. With `timeToLive` set: Redis 7.4+ or Valkey 9.0+, for `HEXPIRE` |
| Vector store, vector-set backend | Redis 8.0+. Not available on Valkey. Enumeration and delete-by-filter need Redis 8.4+ |
| Vector store, Redis Query Engine backend | Redis 8.0+, where the Query Engine is built in. On Redis Stack or Valkey with `valkey-search`, the features the server lacks are reported by `capabilities()` and refused |
| Semantic cache | Whatever its vector store needs. Every operation except `invalidate(prompt)` that removes or lists entries needs enumeration |
| Agent memory | Redis 4.0+ or any Valkey. The optional archive is a vector store and needs what that store needs; with an archive, `clear` and `evictInactiveSince` need enumeration |

### Entry point

`RedissonClient` does not carry the AI objects, because they ship in a separate artifact. They are obtained from `RedissonAI`, which binds to any of the three client flavours:

```java
RedissonClient redisson = Redisson.create(config);

RedissonAI ai = RedissonAI.of(redisson);
RedissonAI.RedissonAIReactive reactive = RedissonAI.of(redisson.reactive());
RedissonAI.RedissonAIRx rx = RedissonAI.of(redisson.rxJava());
```

Construction does blocking I/O - Redis commands, and for the semantic cache one embedding call - and fails at wiring time: `EmbeddingConfigurationException`, `VectorStoreConfigurationException` or `IllegalArgumentException` for a configuration mistake, `UnsupportedByBackendException` when the server lacks the backend, and `VectorStoreBackendException` when a store cannot probe the server. Create objects once at startup and keep them, rather than per request.

## Embedding models

An embedding model turns text into a vector of floats. [REmbeddingModel](https://static.javadoc.io/org.redisson/redisson-ai-core/latest/org/redisson/api/REmbeddingModel.html) is one interface over every provider, so the cache, the vector store, the semantic cache and the agent memory work the same way whichever model is underneath.

Every model declares its identity and shape without any network call: `modelId()` (for example `openai:text-embedding-3-small`), `provider()`, `dimensions()`, `distance()` and `maxBatchSize()`. The width is always declared by you through `nativeDimensions`, never discovered from a first call, so a store can create its index before anything has been embedded.

### Providers

=== "OpenAI"
	```java
	MeterRegistry meters = RedissonAI.of(redisson).getMeterRegistry();

	REmbeddingModel model = OpenAiEmbeddingModel.create(
	        OpenAiEmbeddingOptions.model("text-embedding-3-small")
	                .apiKey(System.getenv("OPENAI_API_KEY"))
	                .nativeDimensions(1536),
	        meters);
	```

=== "Ollama"
	```java
	REmbeddingModel model = OllamaEmbeddingModel.create(
	        OllamaEmbeddingOptions.model("nomic-embed-text")
	                .baseUrl(URI.create("http://localhost:11434"))
	                .nativeDimensions(768),
	        meters);
	```

=== "Spring AI"
	```java
	// wrap an existing Spring AI EmbeddingModel
	REmbeddingModel model = SpringAiBridge.from(springAiEmbeddingModel,
	        "text-embedding-3-small", 1536, meters);

	// or expose a Redisson model to Spring AI
	org.springframework.ai.embedding.EmbeddingModel springModel = SpringAiBridge.to(model);
	```

=== "LangChain4j"
	```java
	// wrap an existing LangChain4j EmbeddingModel
	REmbeddingModel model = LangChain4jBridge.from(langChain4jEmbeddingModel,
	        "all-minilm-l6-v2", 384, meters);

	// or expose a Redisson model to LangChain4j
	dev.langchain4j.model.embedding.EmbeddingModel lc4jModel = LangChain4jBridge.to(model);
	```

=== "Custom function"
	```java
	// an in-process model: ONNX, DJL, a local transformer
	REmbeddingModel model = REmbeddingModel.custom(
	        CustomEmbeddingOptions.of("minilm-onnx", 384, texts -> onnxModel.embed(texts)),
	        meters);
	```

To choose one, take the first row that applies:

| If | Then |
|---|---|
| You already use Spring AI or LangChain4j for models | A bridge. You keep the framework's provider support and gain the Redisson cache, limiters and metrics. A bridge cannot pass `reduceTo`, a per-call `dimensions`, a per-call `timeout` or an `inputType` through, and says so in `supportedParameters()` |
| You need `reduceTo` or a per-call `dimensions` | `redisson-embedding-openai`. Both native adapters, OpenAI and Ollama, also honour a real per-request timeout |
| You run models locally and want no API key | `redisson-embedding-ollama` |
| Your provider is not listed and speaks HTTP | An adapter built on `redisson-embedding-http`, which supplies the transport, the retry policy and `Retry-After` handling. Batching and token-limit batch splitting apply once the adapter declares its batch ceiling, token budget and token-limit detector; without them each text is its own request |
| Your model runs in-process | `REmbeddingModel.custom(...)` |

Do not wrap an HTTP call in `custom(...)` to add a provider: it silently gives up retries, `Retry-After`, batch splitting on token-limit errors and rate-limit header parsing, and works well only until the provider has an incident.

### Embedding text

`embed` embeds one text and `embedAll` embeds a list, returning vectors in input order. Each returned array is the caller's own: modifying one never affects another or a cached copy.

A batch is split into provider requests automatically: up to 2048 texts within a token budget per OpenAI request, one text per Ollama request, and one call for a custom function or bridge unless `batchSize` is set. HTTP providers send all the requests of one batch at once, so set a `concurrencyLimiter` to bound them.

=== "Sync"
	```java
	float[] query = model.embed("how does HNSW work?", EmbedOptions.query());

	List<float[]> docs = model.embedAll(
	        List.of("HNSW is a graph index", "FLAT is an exhaustive scan"),
	        EmbedOptions.document());

	int width = model.dimensions();   // no I/O
	```

=== "Async"
	```java
	RFuture<float[]> query = model.embedAsync("how does HNSW work?", EmbedOptions.query());

	RFuture<List<float[]>> docs = model.embedAllAsync(
	        List.of("HNSW is a graph index", "FLAT is an exhaustive scan"),
	        EmbedOptions.document());
	```

=== "Reactive"
	```java
	// an embedding model is not a Redisson object and has no reactive face;
	// RFuture is a CompletionStage, so bridging is one line.
	// Use the Supplier form so the call is deferred until subscription.
	Mono<float[]> query = Mono.fromCompletionStage(
	        () -> model.embedAsync("how does HNSW work?", EmbedOptions.query()));
	```

=== "RxJava3"
	```java
	Single<float[]> query = Single.defer(() -> Single.fromCompletionStage(
	        model.embedAsync("how does HNSW work?", EmbedOptions.query())));
	```

An async result can complete on a Netty event loop. Do not block in a dependent stage; move to your own executor with `thenApplyAsync(fn, executor)`.

Call `close()` when the application shuts down. It releases what the model owns - the HTTP client and its event loop group unless the group was borrowed, or the thread pool of a custom function or bridge - and propagates through a caching wrapper to the model inside it, without closing the cache. A closed model throws `IllegalStateException`.

### Embed options

[EmbedOptions](https://static.javadoc.io/org.redisson/redisson-ai-core/latest/org/redisson/api/embedding/EmbedOptions.html) configures one call. It is immutable, so a configured instance can be kept as a constant.

| Option | Description | Part of the cache key |
|---|---|---|
| `inputType` | What the text is for: `DOCUMENT`, `QUERY`, `CLASSIFICATION` or `CLUSTERING`. `EmbedOptions.document()` and `EmbedOptions.query()` are shortcuts. Best-effort: a provider that does not distinguish input types embeds normally | yes |
| `dimensions` | A narrower vector for this call, on a provider that supports it | yes |
| `truncate` | `NONE`, `DROP_START` or `DROP_END`, applied client-side against the model's `maxInputChars` | yes |
| `timeout` | Per-request deadline for this call, on HTTP providers | no |
| `tenant` | A label carried with the call and visible to listeners. No built-in component acts on it: it partitions neither rate limits, metrics nor the cache | no |
| `bypassCache` | Skips the embeddings cache on read and write | no |

Defaults for every option can be set once on the model with `defaultOptions(...)`. The options actually used are `model.defaultOptions().overrideWith(callOptions)`: an option the caller set wins, and an option the caller left unset falls back to the default.

A `dimensions` or `truncate` the model cannot honour throws `EmbeddingConfigurationException`, because silently ignoring either would change the vector. An `inputType` or `timeout` the model cannot honour is ignored with a one-time warning in the log. `supportedParameters()` lists what a model honours, and is derived from the same code that makes those decisions:

| Model | Supported parameters |
|---|---|
| OpenAI | `DIMENSIONS`, `REDUCE_TO`, `TRUNCATE`, `TIMEOUT` |
| Ollama | `TRUNCATE`, `TIMEOUT` |
| Spring AI and LangChain4j bridges | `TRUNCATE` |
| Custom function | `TRUNCATE` |

**Choose the input type by whether both sides are the same kind of text.**

| Pattern | What is compared | Setting |
|---|---|---|
| Asymmetric retrieval | Stored documents against user questions | `document()` when writing, `query()` when searching |
| Symmetric matching | Prompt against prompt, utterance against utterance | The same value on both sides |

Getting asymmetric retrieval backwards reduces recall with no error. Getting the symmetric case wrong is worse: on a model that honours input types, a prompt stored as `DOCUMENT` and looked up as `QUERY` lands in a different region of the vector space, and a semantic cache built that way almost never hits. The vector store and the semantic cache set the input type for you.

None of the models shipped today sends `inputType` to its provider - `INPUT_TYPE` is absent from every row of the table above. On them it changes only the cache key, so the same text embedded as `DOCUMENT` and as `QUERY` costs two provider calls and two cache entries holding the same vector. Set it correctly anyway, so that a model which honours it behaves.

### Result metadata

`embedForResult` and `embedAllForResult` return the same vectors together with where they came from and what they cost:

```java
EmbeddingResult result = model.cached(cache)
        .embedAllForResult(texts, EmbedOptions.document());

result.vectors();       // the vectors, in input order
result.origins();       // CACHE or PROVIDER, per element
result.cachedCount();   // how many came from the cache
result.usage();         // tokens the provider charged for the misses, if it reports them
result.rateLimit();     // the provider's rate-limit headers, if collectRateLimitHeaders(true)
```

Origins are per element, because one batch is routinely part cache hit and part provider call.

### Model options

These options are available on every model's options class:

| Option | Description | Default Value |
|---|---|---|
| `batchSize` | Maximum texts per provider request, capped by the provider's own limit | the provider limit: 2048 for OpenAI, 1 for Ollama, unbounded for a custom function or bridge |
| `batchingStrategy` | How a request is filled up to `batchSize`. `TokenBudgetBatchingStrategy` fills to a token budget; `FixedSizeBatchingStrategy` fills by count | token budget for providers with a token limit, fixed size otherwise |
| `maxInputChars` | Character limit that `truncate` applies. While unset, truncation has no effect. For a bridge, pass it to the `from(model, modelId, dimensions, maxInputChars, meters)` overload | unset |
| `rateLimiter` | An `RRateLimiter` shared across the cluster, one permit per provider request. Fails open when unavailable | none |
| `concurrencyLimiter` | An `RPermitExpirableSemaphore` capping in-flight provider requests across the cluster, with a lease so a hung request cannot leak a permit. Fails open when unavailable | none |
| `executor` | Executor that a custom function or bridge model runs its blocking calls on. Never a Redisson event loop. Not used by the OpenAI and Ollama models | a pool per model with max(4, CPU count) threads and a 1000-task queue, which refuses work when full and is shut down by `close()` |
| `defaultOptions` | `EmbedOptions` merged under every call | none |
| `listener` / `listeners` | `EmbeddingModelListener` receiving `onRequest`, `onResponse` and `onError` for each call | none |

HTTP providers add transport options:

| Option | Description | Default Value |
|---|---|---|
| `baseUrl` | Provider endpoint | `https://api.openai.com/v1` for OpenAI, `http://localhost:11434` for Ollama |
| `timeout` | Per-request timeout | 30 seconds |
| `connectTimeout` | Connection timeout | 5 seconds |
| `maxRetries` | Retries after the first attempt | 3 |
| `retryBackoff` | Initial delay, multiplier and maximum delay of the exponential backoff, applied with full jitter | 200 ms, 2.0, 10 seconds |
| `proxy`, `socks5Proxy`, `proxyCredentials` | Proxy selection for provider calls: a `ProxySelector`, or a SOCKS5 proxy with optional credentials | `ProxySelector.getDefault()` |
| `collectRateLimitHeaders` | Parse the provider's rate-limit headers into `EmbeddingResult.rateLimit()` | `false` |
| `httpClientCustomizer` | Hook that adjusts the transport through Redisson's `HttpClientBuilder`: trust store, client certificate for mutual TLS, hostname verification, idle connections per route (8 by default) and request interceptors | none |
| `eventLoopGroup` | Netty event loop group to borrow, usually Redisson's. A borrowed group is never shut down by the model | a single-thread group owned by the model |

OpenAI adds `apiKey`, `organization`, `maxTokensPerRequest` (300 000 by default) and `reduceTo`, which requests narrower vectors from the API and appends `#<width>` to `modelId()`. The API key is mandatory. `apiKey(Supplier<String>)` supports key rotation: the supplier is called on every HTTP attempt, so it must be fast, non-blocking and thread-safe, and a null or empty answer fails the call rather than sending an unauthenticated request. The library never puts the key into `modelId()`, a log line or an exception message.

**The built-in token estimate is rough for non-Latin text.** The default token budget estimates one token per four UTF-8 bytes and keeps 10% in reserve. Measured against `cl100k_base`, that underestimates Chinese by 1.53x, Korean by 1.77x, Hebrew by 2.03x and emoji by 2.77x. A request OpenAI rejects as too many tokens is split in half and retried, recursively down to single texts, so a batch whose texts each fit still succeeds - but each split costs a round trip, counted by `redisson.embedding.batch.oversized`. A single text over the model's input limit cannot be split and fails with `TokenLimitException`; `truncate` with `maxInputChars` prevents that. For a corpus that is not mostly Latin script, supply a real tokenizer:

```java
OpenAiEmbeddingOptions.model("text-embedding-3-small")
        .apiKey(key)
        .nativeDimensions(1536)
        .batchingStrategy(new TokenBudgetBatchingStrategy(tokenizer::count, 300_000));
```

### Retries and errors

HTTP providers share one retry policy:

| Condition | Action |
|---|---|
| HTTP 429 | Retry, honouring `Retry-After` when present, otherwise with backoff |
| HTTP 5xx | Retry with backoff |
| Connection failure, reset or timeout | Retry with backoff |
| TLS failure or unknown host | Fail immediately |
| HTTP 400 that the adapter recognises as a token limit (OpenAI) | Split the batch in half and retry, down to a single text |
| Any other HTTP status | Fail immediately, with the response body attached |
| Malformed response | Fail immediately |

`Retry-After` is honoured in its seconds form. Retrying stops when `maxRetries` is reached or the retry budget - derived from the timeout and backoff settings - is spent, and a `Retry-After` is capped at what is left of that budget.

All embedding exceptions extend `REmbeddingException`:

| Exception | Thrown when |
|---|---|
| `EmbeddingConfigurationException` | The configuration is invalid or an option is not supported by the model |
| `EmbeddingProviderException` | The provider rejected the request, was unreachable, or returned something that could not be parsed. Carries the HTTP status (`-1` if no response arrived) and the provider's response body verbatim |
| `EmbeddingRateLimitException` | The provider's quota was exhausted and retries did not recover. Extends `EmbeddingProviderException` and carries the `Retry-After` value |
| `TokenLimitException` | A single text still exceeded the provider's token limit after its batch was split down to it. Extends `EmbeddingProviderException`; in `org.redisson.embedding.http` |
| `EmbeddingTimeoutException` | A connect or read timeout persisted through every retry |
| `EmbeddingDimensionMismatchException` | A vector came back at a width other than the declared one |

## Embeddings cache

Java implementation of Valkey or Redis based [REmbeddingsCache](https://static.javadoc.io/org.redisson/redisson-ai-core/latest/org/redisson/api/REmbeddingsCache.html) object stores computed vectors keyed by the text they were computed from, the model and the options that affect the output. A text embedded once is not sent to the provider again for the same model and options. The text itself is hashed and never stored.

The usual way to use it is to wrap a model with `cached(cache)`. The wrapped model has the same interface, looks every text up before calling the provider, sends only the misses, and stores what comes back before returning. A batch with a text repeated inside it pays for that text once.

`cached(cache)` reads the cache's metadata, so it does I/O and throws `EmbeddingConfigurationException` if the cache records a different width for the same model id. It also moves the model's listeners to the wrapper, which then reports each whole call, cache hits included; the unwrapped model stops raising events, even if called directly.

```java
REmbeddingsCache cache = RedissonAI.of(redisson).getEmbeddingsCache(
        EmbeddingsCacheOptions.name("docs"));

REmbeddingModel model = openai.cached(cache);

List<float[]> vectors = model.embedAll(
        List.of("chunk one", "chunk two", "chunk one"),   // one provider slot for "chunk one"
        EmbedOptions.document());
```

The cache can also be used directly:

=== "Sync"
	```java
	REmbeddingsCache cache = RedissonAI.of(redisson).getEmbeddingsCache("docs");

	cache.put("hello", model.modelId(), EmbedOptions.document(), vector);
	Optional<float[]> hit = cache.get("hello", model.modelId(), EmbedOptions.document());

	Map<String, float[]> hits = cache.getAll(texts, model.modelId(), EmbedOptions.document());
	boolean removed = cache.remove("hello", model.modelId(), EmbedOptions.document());
	long entries = cache.size(model.modelId());
	```

=== "Async"
	```java
	REmbeddingsCacheAsync cache = RedissonAI.of(redisson).getEmbeddingsCache("docs");

	RFuture<Void> put = cache.putAsync("hello", model.modelId(), EmbedOptions.document(), vector);
	RFuture<float[]> hit = cache.getAsync("hello", model.modelId(), EmbedOptions.document()); // null on a miss
	RFuture<Long> entries = cache.sizeAsync(model.modelId());
	```

=== "Reactive"
	```java
	REmbeddingsCacheReactive cache = RedissonAI.of(redisson.reactive()).getEmbeddingsCache("docs");

	Mono<Void> put = cache.put("hello", model.modelId(), EmbedOptions.document(), vector);
	Mono<float[]> hit = cache.get("hello", model.modelId(), EmbedOptions.document()); // empty on a miss
	Mono<Long> entries = cache.size(model.modelId());
	```

=== "RxJava3"
	```java
	REmbeddingsCacheRx cache = RedissonAI.of(redisson.rxJava()).getEmbeddingsCache("docs");

	Completable put = cache.put("hello", model.modelId(), EmbedOptions.document(), vector);
	Maybe<float[]> hit = cache.get("hello", model.modelId(), EmbedOptions.document()); // empty on a miss
	Single<Long> entries = cache.size(model.modelId());
	```

### Cache options

| Option | Description | Default Value |
|---|---|---|
| `name` | Cache name, matching `[A-Za-z0-9._-]+` | required |
| `timeToLive` | Per-entry expiry in whole seconds, set on write and not extended by reads. Needs Redis 7.4+ or Valkey 9.0+; construction throws on an older server | no expiration |
| `normalization` | Unicode normalization applied before hashing: `NONE`, `NFC` or `NFKC`. Fixed by the first client to open the cache; a client configured differently fails at construction | `NFC` |
| `fieldDerivation` | Key the text hash is derived under. `1` is a random per-cache secret stored in Redis; `2` is Redisson's published constant, which makes entries computable offline. Changing it on a cache that holds entries does not fail: the old entries are never hit again and stay until `removeModel` or `clear`, and a `WARN` says so | `1` |
| `singleFlight` | Take a cluster-wide lock on a miss, so concurrent callers embedding the same text make one provider call. Waits up to 5 seconds for the lock, then proceeds without it. Batch and async paths never lock | `false` |
| `maxTextLength` | Texts longer than this, in characters, are neither read from nor written to the cache. The embed call proceeds normally | unlimited |

Leaving `timeToLive` unset is usually right. A text's vector never changes for a given model, so there is nothing to invalidate, and a TTL only trades provider spend for memory.

### What is in the key

A cache entry is identified by the model id, the text after normalization, and the options that change the output: `inputType`, `dimensions` and `truncate`. The values used are the effective ones, after the model's `defaultOptions` are merged in.

The key describes the text as supplied, not as truncated, and `maxInputChars` is not part of it. After changing `maxInputChars`, or anything else that changes the vectors without changing `modelId()`, drop the model's entries with `removeModel` or use a new cache name.

**`tenant` does not partition the cache, on purpose.** The vector of "I prefer vegetarian food" is the same whoever wrote it, and sharing it across users is most of the cache's value. Where two tenants must not share entries, give each its own cache name.

### Model rotation

Different models occupy different keys, so an old and a new model can be live at the same time. Once the read path has moved, drop the old model's entries in one call:

```java
REmbeddingModel v2 = OpenAiEmbeddingModel.create(
        OpenAiEmbeddingOptions.model("text-embedding-3-large")
                .apiKey(key)
                .nativeDimensions(3072),
        meters).cached(cache);

// ... backfill, then switch reads to v2 ...

long dropped = cache.removeModel("openai:text-embedding-3-small");
```

### Failure behaviour

The cache is an optimisation, so a Valkey or Redis problem never fails an embed call:

| Situation | Behaviour |
|---|---|
| Cache read fails | Treated as a miss, `redisson.embedding.cache.errors` is incremented, and the provider is called |
| Cache write fails | Logged at `DEBUG`, counted in `redisson.embedding.cache.errors`, and otherwise ignored |
| Single-flight lock unavailable | The call proceeds without it |
| Rate limiter or concurrency limiter unavailable | The call proceeds unthrottled, with a rate-limited `WARN` |
| Two models share an id at different widths | `EmbeddingConfigurationException`. This one is not ignored, because every read would miss and both models would overwrite each other |

**How long a cache call may wait is the Redisson client's decision.** The cache uses the client it was created from, so with Redisson's defaults (3 second timeout, 4 retry attempts) a hung server holds an embed call for around 20 seconds before it falls back to the provider, and about as long again while the computed vector is written back, because the call returns only after that write settles. If cache latency matters, create the cache from a client configured for it:

```java
Config config = new Config();
config.useSingleServer()
      .setAddress("redis://127.0.0.1:6379")
      .setTimeout(200)          // a cache lookup is not worth 3 seconds
      .setRetryAttempts(0);     // nor a retry: the provider is the fallback

REmbeddingsCache cache = RedissonAI.of(Redisson.create(config)).getEmbeddingsCache("docs");
```

### Privacy and erasure

Text is hashed with HighwayHash-128 and never stored. With the default `fieldDerivation(1)` the hash is keyed with a per-cache secret, so someone without access to Redis cannot confirm whether a given text was embedded. This is not anonymisation: the vectors themselves are derived personal data, because embedding inversion can recover much of a short text.

Entries cannot be enumerated. To erase one text, call `remove(text, modelId, options)` with the same effective `inputType`, `dimensions` and `truncate` the entry was written with (for a cached model, after its `defaultOptions` are merged in), for every model the text was embedded under - an entry written as `DOCUMENT` is not removed by a `QUERY` removal.

`getStats()` returns hits, misses, puts, errors and the hit rate counted by this cache object, overall and per model, without any I/O; each `getEmbeddingsCache` call returns a new object with its own counters. `clear()` removes the entries of every model in the cache. `destroy()` is a local teardown: it deregisters the meters of this cache name, which every object for that name in the JVM shares, and deletes no data.

### Sizing

Each entry costs about 75 bytes plus the vector:

| Entries | 384 dimensions | 1536 dimensions |
|---|---|---|
| 100 000 | ~161 MB | ~622 MB |
| 1 000 000 | ~1.6 GB | ~6.2 GB |
| 10 000 000 | ~16 GB | ~62 GB |

Add about 10% when `timeToLive` is set. A single cache lives in one hash slot and is never sharded, so above roughly 5 million entries split the data across several cache names.

## Vector store

Java implementation of Valkey or Redis based [RVectorStore](https://static.javadoc.io/org.redisson/redisson-ai-core/latest/org/redisson/api/RVectorStore.html) object stores records - an id, the vector of a text, filterable attributes and an optional JSON document - and searches them by similarity. Text goes in and the store embeds it through its embedding model, so one call writes and one call searches. It runs on either of the two vector engines of Redis, chosen by which options object you build:

- **Vector sets** - one key holding the whole HNSW graph, through the `VADD` and `VSIM` commands. See also the low-level [Vector Set](collections.md/#vector-set) object.
- **The Redis Query Engine** - an index over hash keys, through `FT.CREATE` and `FT.SEARCH`. See also the low-level [RediSearch service](services.md/#redisearch-service).

The API is identical on both backends. Where the backends differ, the difference is declared in `capabilities()` and a call the backend cannot serve throws `UnsupportedByBackendException`. Pure tuning hints are the exception: an option that changes only effort, such as `filterEffort` on the Query Engine, is ignored where it does not apply.

=== "Sync"
	```java
	RVectorStore<Article> store = RedissonAI.of(redisson).getVectorStore(
	        VectorSetStoreOptions.name("articles")
	                .embeddingModel(model)
	                .payloadType(Article.class)
	                .payloadCodec(new JacksonCodec<>(Article.class)));

	store.add(VectorRecord.of("a-1", "Redis is an in-memory data store")
	        .filterable("year", 2026)
	        .payload(article));

	List<VectorMatch<Article>> hits = store.search(
	        SearchQuery.text("what is redis").k(5).minSimilarity(0.75));
	```

=== "Async"
	```java
	RVectorStoreAsync<Article> store = RedissonAI.of(redisson).getVectorStore(
	        VectorSetStoreOptions.name("articles")
	                .embeddingModel(model)
	                .payloadType(Article.class)
	                .payloadCodec(new JacksonCodec<>(Article.class)));

	RFuture<Boolean> added = store.addAsync(VectorRecord.of("a-1", "Redis is an in-memory data store")
	        .filterable("year", 2026)
	        .payload(article));

	RFuture<List<VectorMatch<Article>>> hits = store.searchAsync(
	        SearchQuery.text("what is redis").k(5).minSimilarity(0.75));
	```

=== "Reactive"
	```java
	RVectorStoreReactive<Article> store = RedissonAI.of(redisson.reactive()).getVectorStore(
	        VectorSetStoreOptions.name("articles")
	                .embeddingModel(model)
	                .payloadType(Article.class)
	                .payloadCodec(new JacksonCodec<>(Article.class)));

	Mono<Boolean> added = store.add(VectorRecord.of("a-1", "Redis is an in-memory data store")
	        .filterable("year", 2026)
	        .payload(article));

	Mono<List<VectorMatch<Article>>> hits = store.search(
	        SearchQuery.text("what is redis").k(5).minSimilarity(0.75));
	Flux<String> ids = store.ids();
	```

=== "RxJava3"
	```java
	RVectorStoreRx<Article> store = RedissonAI.of(redisson.rxJava()).getVectorStore(
	        VectorSetStoreOptions.name("articles")
	                .embeddingModel(model)
	                .payloadType(Article.class)
	                .payloadCodec(new JacksonCodec<>(Article.class)));

	Single<Boolean> added = store.add(VectorRecord.of("a-1", "Redis is an in-memory data store")
	        .filterable("year", 2026)
	        .payload(article));

	Single<List<VectorMatch<Article>>> hits = store.search(
	        SearchQuery.text("what is redis").k(5).minSimilarity(0.75));
	Flowable<String> ids = store.ids();
	```

### Choosing a backend

| | Vector sets | Redis Query Engine |
|---|---|---|
| Server | Redis 8.0+. Not available on Valkey | Redis 8.0+, Redis Stack, or `valkey-search` |
| Storage | The whole graph is one key, and a store's keys share one hash slot, so one store lives on one shard | Hash keys spread across shards; the server merges a correct top-k |
| Distance metrics | Cosine only | Cosine, Euclidean and inner product |
| Memory per vector | 8-bit quantized by default, about 4x smaller than FP32 | FP32 |
| Filtering | Declaring a schema is optional and only validates, except that a store with a `filterPolicy` requires one | A `filterableSchema` is required for every field you filter on |
| Full-text and GEO predicates | No | Through `nativeFilter` |
| Index structure | HNSW | HNSW or FLAT (exact) |
| Reading a vector back | Approximate: normalized and quantized on insert | Exact |

**Vector sets** are the simpler default, and the better fit when memory is the constraint or when you want many small isolated stores, for example one per tenant, which spread naturally across the shards of a cluster. Vector sets shipped in Redis 8.0 labelled as a preview; test failover against your own workload before relying on them in a primary-replica topology.

**The Redis Query Engine** is the choice on Valkey, when one store must outgrow one shard's memory, when you need full-text or GEO predicates, or when your model is not trained for cosine.

The backend is fixed when the store is created. There is no automatic selection and no fallback between them.

### Creating a store

=== "Vector sets"
	```java
	RVectorStore<Article> store = RedissonAI.of(redisson).getVectorStore(
	        VectorSetStoreOptions.name("articles")
	                .embeddingModel(model)
	                .payloadType(Article.class)
	                .payloadCodec(new JacksonCodec<>(Article.class))
	                .quantization(QuantizationType.Q8)
	                .filterableSchema(Map.of(
	                        "year", FieldType.NUMERIC,
	                        "topic", FieldType.TAG)));
	```

=== "Query Engine, HNSW"
	```java
	RSearchVectorStore<Article> store = RedissonAI.of(redisson).getVectorStore(
	        RediSearchStoreOptions.hnsw("articles")
	                .embeddingModel(model)
	                .payloadType(Article.class)
	                .payloadCodec(new JacksonCodec<>(Article.class))
	                .m(32)
	                .filterableSchema(Map.of(
	                        "year", FieldType.NUMERIC,
	                        "topic", FieldType.TAG)));
	```

=== "Query Engine, FLAT"
	```java
	RSearchVectorStore<Article> store = RedissonAI.of(redisson).getVectorStore(
	        RediSearchStoreOptions.flat("articles")
	                .embeddingModel(model)
	                .payloadType(Article.class)
	                .payloadCodec(new JacksonCodec<>(Article.class))
	                .filterableSchema(Map.of("topic", FieldType.TAG)));
	```

Store names must match `[A-Za-z0-9._-]+`. Construction probes the server, checks that the model's width and distance metric agree with the store's, writes or reads the store's metadata, and creates the index on the Query Engine; a vector set's key appears with the first write. Reopening an existing store with a different width, metric, quantization or `reduceTo` throws there, rather than at the first write; a different model id only logs a warning.

Omitting `payloadType` produces a store over `Void` that keeps no documents - the right shape for a semantic router or a deduplication index, where the id and the score are the whole answer. When you chain calls on a record for such a store, give the type explicitly: `VectorRecord.<Void>of(id, text).filterable(...)`.

Options available on both backends:

| Option | Description | Default Value |
|---|---|---|
| `embeddingModel` | The model that embeds records and query text. Required | |
| `payloadType` | Class of the JSON document stored beside each vector | none, a store over `Void` |
| `payloadCodec` | `JsonCodec` for the documents. Required when `payloadType` is set | |
| `dimensions` | Expected vector width. A guard: construction throws if the model disagrees | the model's width |
| `batchSize` | Records per write chunk and per read round trip, at most 10 000 | 500 |
| `maxPayloadBytes` | Largest serialized document accepted | 1 MiB |
| `maxFilterableKeys` | Most filterable attributes on one record | 64 |
| `maxFilterableBytes` | Largest serialized attribute set on one record. Attributes travel with every search hit, so keep them small | 16 KiB |
| `filterPolicy` | A predicate every filtered operation must satisfy. See [Scoping a store per tenant](#scoping-a-store-per-tenant) | none |
| `meterName` | Value of the `store` tag on the store's meters. Set it when the store name contains a tenant id or anything else unbounded | the store name |

Vector-set options:

| Option | Description | Default Value |
|---|---|---|
| `quantization` | `Q8`, `BIN` (about 32x smaller than FP32, with a data-dependent recall loss) or `NOQUANT`. Fixed when the key is created | `Q8` |
| `reduceTo` | Random projection to fewer dimensions, done by the server. Costs a lot of recall; prefer a model's own dimension reduction | none |
| `m` | Maximum connections per graph node, 4 to 4096. Fixed at creation | 16 |
| `buildExplorationFactor` | How hard insertion searches for neighbours. Higher builds a better graph, more slowly | 200 |
| `filterableSchema` | Declared field types. Sent nowhere. It rejects a filter on a misspelled field, which a vector set would otherwise treat as a silent non-match, and a filter or a write whose value type contradicts the declaration - a number compared with a text field can otherwise match every record. Required when `filterPolicy` is set | none |

Query Engine options:

| Option | Description | Default Value |
|---|---|---|
| `distance` | `COSINE`, `EUCLIDEAN` or `INNER_PRODUCT`. Must match the model's metric | `COSINE` |
| `filterableSchema` | Indexed attributes and their `FieldType`: `TAG`, `NUMERIC`, `TEXT` or `GEO`, at most 1024 fields. Every `TAG` is created case-sensitive; every field indexes missing values and every `TAG` indexes empty values where the server supports it, so filters behave the same on both backends. `TEXT` and `GEO` fields are reachable only through `nativeFilter` | none |
| `m` | HNSW only. Maximum connections per graph node | 16 |
| `efConstruction` | HNSW only. Build-time exploration factor | 200 |
| `efRuntime` | HNSW only. Default query-time exploration factor | 10 |
| `epsilon` | HNSW only. Range widening for similarity-threshold queries | 0.01 |

The HNSW parameters exist only on the options that `hnsw(...)` returns, so setting one on a FLAT index does not compile. Nothing about a vector field can change after the index is created: changing the width, metric or graph parameters means building a new store.

### Adding records

A record is built with `VectorRecord.of(id, text)`, or `VectorRecord.ofVector(id, vector)` when you already hold the vector and no model call should be made.

```java
store.add(VectorRecord.of("doc-17#3", chunkText)
        .filterable("tenant", "acme")
        .filterable("year", 2026)
        .filterable("tags", List.of("billing", "refunds"))
        .payload(new Chunk("doc-17", 3, chunkText)));

UpsertResult result = store.addAll(records);
if (!result.failed().isEmpty()) {
    // batches are not atomic across the whole list: retry these ids
    log.warn("{} records failed", result.failed().size(), result.firstFailure().orElse(null));
}
```

- **The id is yours.** The store never generates one. Up to 512 bytes, non-empty.
- **`add` is an upsert.** Adding an existing id replaces its vector, attributes and document.
- **A record is written all or nothing** - its vector, attributes and document together. Across `addAll`, chunks may succeed or fail independently, and `UpsertResult` reports `created()`, `updated()`, `failed()` and `firstFailure()`.
- **A filterable value is a `String`, `Boolean` or `Number`, or a non-empty list of non-empty strings or booleans.** A list needs a `TAG` field where a schema is declared, and is queried with `VectorFilter.has`; a number inside a list is refused.
- **Invalid attributes are refused before any embedding is paid for**, with `VectorStoreConfigurationException`: a name outside `[A-Za-z0-9_-]` or one of the reserved names `__v` and `__p`, a null value, a string with leading or trailing whitespace, longer than 4096 bytes or containing both `'` and `"`, an integer beyond ±2^53, NaN or infinity, and a value whose type contradicts a declared schema.
- **The text is not stored.** The store keeps the vector derived from it; keep the text in the document if you will need it again, for example to re-embed under a new model.

`updateFilterable(id, map)` replaces a record's attributes and `updatePayload(id, payload)` replaces its document, both without re-embedding. Use them for metadata changes: `add` on an existing id pays for a new embedding.

Documents are written with each record's `inputType` set to `DOCUMENT` and queries with `QUERY`, which is what retrieval models expect. `VectorRecord.embedOptions(...)` and `SearchQuery.embedOptions(...)` add options on top of those defaults.

### Searching

```java
List<VectorMatch<Chunk>> hits = store.search(SearchQuery.text("how do refunds work")
        .k(10)
        .minSimilarity(0.7)
        .filter(VectorFilter.eq("tenant", "acme")));

for (VectorMatch<Chunk> hit : hits) {
    hit.id();
    hit.similarity();   // normalized, in [0, 1]; empty for non-cosine metrics
    hit.rawScore();     // the backend's own score: a similarity on vector sets, a distance on the Query Engine
    hit.filterable();   // attributes; on the Query Engine only the schema-declared ones
    hit.payload();      // the document, when fetched
}

// shortcuts
List<VectorMatch<Chunk>> byText = store.search("how do refunds work", 10);
List<VectorMatch<Chunk>> similar = store.searchSimilarTo("doc-17#3", 5);   // no re-embedding
```

**Scores mean the same thing on both backends.** Vector sets report a similarity where higher is better and the Query Engine reports a cosine distance where lower is better, so a raw threshold would mean opposite things. Every match on a cosine store therefore carries a normalized similarity, `(1 + cos) / 2`, which lands on the identical value for the identical pair of vectors on either backend. `minSimilarity` thresholds in that space, so a threshold survives a change of backend. Matches come back best first, with ties broken by id.

A query starts from text, from a vector, or from a stored record:

| Option | Description | Default Value |
|---|---|---|
| `SearchQuery.text(text)` | Search by text, embedded by the store's model | |
| `SearchQuery.vector(vector)` | Search by a vector you already have. No model call | |
| `SearchQuery.element(id)` | Nearest neighbours of a stored record, from its stored vector. An unknown id returns no results | |
| `k` | Upper bound on the number of results, at most 1 000 000. Fewer is normal, especially when filtered | 10 |
| `minSimilarity` | Cutoff on the normalized similarity, in `[0, 1]`. Cosine stores only | none |
| `maxRawScore` | Cutoff on `rawScore()`, in the backend's own orientation: a maximum distance on the Query Engine (for inner product the distance is `1 - u·v`), a minimum similarity on vector sets. Meant for Euclidean and inner product; mutually exclusive with `minSimilarity` | none |
| `filter` | A typed `VectorFilter`. See [Filtering](#filtering) | none |
| `nativeFilter` | A filter in the backend's own syntax, passed through unescaped. Mutually exclusive with `filter` | none |
| `withPayload` | Whether to fetch documents | `true` |
| `payloadFields` | JSON paths of each document to fetch, such as `$.title`. See [Payload projection](#payload-projection) before using it | whole document |
| `explorationFactor` | Graph search effort. Higher is slower and more accurate | `max(100, k)` on vector sets, `efRuntime` on HNSW |
| `filterEffort` | Vector sets only: how many candidates a filtered search examines before giving up. Ignored on the Query Engine | `k * 100` |
| `exact` | Exhaustive O(N) scan instead of graph traversal, and the only way to get a reproducible result set. Vector sets and FLAT indexes; refused on an HNSW index | `false` |
| `timeout` | Server-side time limit. Query Engine only; when it fires, the server's default policy returns the results found so far rather than an error. Refused on vector sets, where the Redisson client timeout is the bound | none |
| `embedOptions` | Options for embedding the query text, merged over `QUERY` | none |

`queryVector(text, options)` returns the vector a search would use for a text, without searching. Use it when one text drives several searches, so the model is called once.

Approximate search returns the same ordering for the same query and data, but not necessarily the same set: graph traversal depends on insertion order, and a replica can answer differently from its primary. Tests that assert an exact result set must use `exact(true)`, or a FLAT index.

### Filtering

[VectorFilter](https://static.javadoc.io/org.redisson/redisson-ai-core/latest/org/redisson/api/vector/VectorFilter.html) is a typed filter tree over the records' filterable attributes. One tree compiles to the filter language of either backend, and every value is escaped or delimited by the compiler. Field names are limited to letters, digits, `_` and `-`, and on vector sets a value containing both `'` and `"` cannot be expressed and throws:

```java
VectorFilter recent = VectorFilter.and(
        VectorFilter.eq("topic", "databases"),
        VectorFilter.gte("year", 2024),
        VectorFilter.not(VectorFilter.eq("status", "draft")));

List<VectorMatch<Chunk>> hits = store.search(
        SearchQuery.text("filtering a vector search").k(10).filter(recent));
```

| Operator | Matches records where |
|---|---|
| `eq(field, value)`, `ne(field, value)` | The field equals, or does not equal, a string, number or boolean |
| `gt`, `gte`, `lt`, `lte` | The numeric field compares to a bound |
| `between(field, lo, hi)` | The numeric field is within an inclusive range |
| `in(field, strings)`, `inNumbers(field, numbers)` | The field is one of the values - set membership, never substring matching |
| `has(field, value)` | A multi-valued field contains the string. Use it for list attributes; `eq` is not containment. On a scalar field a vector set does a substring test, so do not use it there, and never in a policy over a scalar field |
| `exists(field)` | The field is present |
| `notExists(field)` | The field is absent. Query Engine only |
| `and(...)`, `or(...)`, `not(...)` | Combinators |
| `all()` | Every record. Explicit, so that an unbounded predicate is visible |

**Read the missing-field rule before using `or` or `not`.** A record that lacks any field named anywhere in the tree, other than by `notExists`, does not match, whatever the boolean structure above it. So `or(eq("a", 1), eq("b", 2))` does **not** match a record that has only `b`. This is the only rule both backends can implement, because a vector set abandons the whole expression as soon as a named field is missing - and it fails closed: a misspelled field name empties the result rather than widening it.

A filter that cannot be expressed exactly on the store's backend throws before any command is sent - a field missing from a Query Engine schema, a range over a `TAG` field, `notExists` on a vector set. There is no approximation and no dropped clause, because both would return plausible wrong answers.

**On vector sets, a filtered search can return fewer than `k` results even when more matching records exist**, and a more selective filter makes that more likely. The graph traversal keeps walking until it finds `k` neighbours that also satisfy the filter, and gives up after a bounded effort. Measured on 20 000 records with `k` of 10, a filter matching 20 records returned two results at the default effort and ten with `filterEffort(200000)`. Raising the effort costs latency; `exact(true)` removes the problem at O(N). The Query Engine applies the filter before ranking, so it returns `k` results whenever `k` matching records exist.

`nativeFilter` remains available for what the tree cannot express - full-text and GEO predicates on the Query Engine, for example. It is not escaped for you: never concatenate user input into it.

```java
// vector sets
SearchQuery.text(question).nativeFilter(".year >= 2024 and .topic == \"databases\"");

// Redis Query Engine
SearchQuery.text(question).nativeFilter("@year:[2024 +inf] @topic:{databases}");
```

### Scoping a store per tenant

There are three ways to restrict what a query can see. Take the first that applies:

| If | Then |
|---|---|
| It is a security boundary and there are few tenants | A store per tenant. A query cannot reach another tenant even if every filter is wrong |
| It is a security boundary and there are too many tenants for that | A `filterPolicy` |
| It is an ordinary query restriction | `SearchQuery.filter(...)` |

A filter policy is a predicate the store adds to every filtered operation and a caller cannot remove:

```java
RVectorStore<Chunk> store = RedissonAI.of(redisson).getVectorStore(
        RediSearchStoreOptions.hnsw("kb")
                .payloadType(Chunk.class)
                .payloadCodec(new JacksonCodec<>(Chunk.class))
                .embeddingModel(model)
                .filterableSchema(Map.of("tenant", FieldType.TAG, "year", FieldType.NUMERIC))
                .filterPolicy(() -> VectorFilter.eq("tenant", TenantContext.current())));
```

A store with a policy must declare a `filterableSchema` covering the policy's fields, on vector sets too; construction throws otherwise. `mandatory()` is called once per operation and never cached, so it may read request-scoped state. It must be fast and thread-safe, must not return `null`, and returns `VectorFilter.all()` for "no restriction here".

The policy covers `search`, `searchSimilarTo`, `ids`, `records` and `deleteByFilter`. It also covers every operation addressed by id: `getPayload`, `getPayloads`, `getPayloadFields`, `getFilterable`, `getVector`, `contains`, `remove`, `removeAll`, `updateFilterable` and `updatePayload` act on a record outside the policy exactly as on an id that does not exist. The routes around it are closed: `nativeFilter`, `ids(String)` and `deleteByFilter(String, ...)` throw, `getNativeClient()` returns empty, and `destroy()` throws - to erase the current tenant's records use `deleteByFilter(VectorFilter.all(), BulkOptions.defaults().allowAll(true))`.

`size()` and `stats()` still report the whole store. A tenant that must not learn the global record count needs its own store. The policy is enforced by this library, not by the server: anything talking to Redis directly bypasses it.

### Reading, enumerating and deleting

```java
Optional<Chunk> doc = store.getPayload("doc-17#3");
Map<String, Chunk> docs = store.getPayloads(List.of("doc-17#3", "doc-17#4"));
Optional<String> titleJson = store.getPayloadFields("doc-17#3", String.class, "$.title");
Optional<Map<String, Object>> attrs = store.getFilterable("doc-17#3");
boolean present = store.contains("doc-17#3");

store.remove("doc-17#3");
store.removeAll(List.of("doc-17#4", "doc-17#5"));
```

`ids(...)` and `records(...)` walk the store, optionally narrowed by a filter. They return streams that can hold a server-side cursor, so always close them:

```java
try (Stream<String> ids = store.ids(VectorFilter.eq("tenant", "acme"))) {
    ids.forEach(id -> audit(id));
}

// re-embed one tenant's records under a new model
try (Stream<VectorRecord<Chunk>> records = store.records(false, VectorFilter.eq("tenant", "acme"))) {
    records.forEach(r -> newStore.add(VectorRecord.of(r.id(), r.payload().orElseThrow().text())
            .filterable(r.filterable())
            .payload(r.payload().orElseThrow())));
}
```

Enumeration is in backend order, not relevance order, and is not a snapshot: a record written or deleted during the walk may or may not appear. On vector sets a filtered walk - which includes every walk on a store with a filter policy - is one exhaustive scan that loads all matching ids at once.

#### Payload projection

`getPayloadFields(id, type, paths)` and `SearchQuery.payloadFields(...)` are meant to fetch only part of a document. What they do today depends on the backend:

- `type` must be the store's payload type, a supertype of it, or `String.class`; any other type throws `PayloadStoreException`.
- **Vector sets with RedisJSON** project on the server, and the result is the raw JSONPath result - a JSON array such as `["The title"]`. Read it with `String.class`. Decoding it into the payload type fails, so a search with `payloadFields` returns its matches with an empty `payload()`.
- **The Query Engine** stores each document inside the record's hash, so the paths are ignored and the whole document is returned, even though `supportsPayloadProjection()` reports `true` where RedisJSON is present.
- **Without RedisJSON** the whole document is fetched and decoded into the requested type.

For a typed document, fetch it whole with `getPayload` or `withPayload(true)`.

`deleteByFilter` removes every record matching a filter - the operation behind erasing one user's or one tenant's data. It refuses an unbounded `VectorFilter`, one that does not restrict a field to a finite set of values, with `IllegalArgumentException` unless `allowAll(true)` is set. The native-string overload can refuse only a blank filter or `*`, so a string such as `".year >= 0"` passes and empties the store. An erasure that fails part way throws `VectorStoreBackendException` with the counts so far, and running it again is safe:

```java
BulkResult preview = store.deleteByFilter(VectorFilter.eq("user", userId),
        BulkOptions.defaults().dryRun(true));

BulkResult result = store.deleteByFilter(VectorFilter.eq("user", userId), BulkOptions.defaults());
result.matched();
result.processed();
```

| `BulkOptions` | Description | Default Value |
|---|---|---|
| `dryRun` | Count what would be removed without removing it | `false` |
| `allowAll` | Permit an unbounded filter | `false` |
| `batchSize` | Ids deleted per round trip | the store's `batchSize` |
| `onProgress` | Callback called after each batch with `(processed, matched)`. A callback that throws fails the deletion | none |
| `executor` | Executor the progress callback runs on. Supply one if the callback is slow | Redisson's worker pool |

Erasure by attribute works only if the attribute was written with the record: a subject id added later cannot find records written before it. Decide at write time which attributes you will need to delete by.

`destroy()` removes every record, every document and the index; the store object cannot write afterwards, so get a new one from `getVectorStore` to start again. `size()` returns the number of records, and `stats()` returns the record count, the document count and memory use. On vector sets a gap between the two counts reveals orphaned documents; on the Query Engine the document lives in the record's hash, so the counts are always equal and memory use is not reported.

### Capabilities

`capabilities()` describes what the store can do on the server it is connected to. `backend()`, `parameters()`, `dimensions()` and `distance()` describe its configuration.

| Capability | Vector sets | Redis Query Engine |
|---|---|---|
| `supportedMetrics()` | Cosine | Cosine, Euclidean, inner product |
| `supportsTextFilter()`, `supportsGeoFilter()` | no | yes, except on `valkey-search` |
| `supportsRangeQuery()` | no | yes, except on `valkey-search` |
| `supportsAliases()` | no | yes, except on `valkey-search` |
| `supportsClusterScaleOut()` | no | yes |
| `supportsExactSearch()` | yes | FLAT indexes |
| `supportsEnumeration()` | Redis 8.4+ | yes, except on `valkey-search` |
| `supportsPayloadProjection()` | where RedisJSON is present | reported where RedisJSON is present, but documents are always fetched whole |
| `exactVectorReadback()` | no | yes |

`supportsEnumeration()` gates `ids`, `records` and `deleteByFilter`, and therefore erasure by attribute. The capabilities are probed once per client and not re-probed after a failover. `supportsHybrid()` reports whether the server has `FT.HYBRID`, but this release has no hybrid search API, and `supportsOffset()` is `false` everywhere.

`getNativeClient()` returns the underlying Redisson object - an `RVectorSet` or an `RSearch` - for anything this API does not cover, and is empty on a store with a filter policy.

A search on the Query Engine that uses a negation, a disjunction across fields or an existence test needs a server that supports missing-value indexing (`INDEXMISSING`), as Redis 8 does. On a server without it the index is still created, and those filters throw while a conjunction of positive terms keeps working.

Store exceptions extend `RVectorStoreException`:

| Exception | Thrown when |
|---|---|
| `VectorStoreConfigurationException` | The configuration or a record is invalid |
| `VectorStoreBackendException` | The server refused, failed or could not be reached, after Redisson's own retries |
| `UnsupportedByBackendException` | The backend, on this server, cannot do what was asked. Extends `VectorStoreBackendException` |
| `PayloadStoreException` | A document could not be encoded on `add`, `addAll` or `updatePayload`, or could not be read or decoded by `getPayload` or `getPayloadFields`. During a search a failed document read returns the matches with an empty `payload()`, and `getPayloads` leaves such documents out |

Two other failures are plain Java exceptions and are not caught by `catch (RVectorStoreException e)`: `IllegalArgumentException` for a malformed filter tree (a bad field name, an empty `in`, `and()` with no children, a tree deeper than 64 or larger than 1024 nodes) and for an unbounded `deleteByFilter` without `allowAll`, and `IllegalStateException` for `filter` combined with `nativeFilter`, a native filter or `destroy()` on a store with a policy, and a policy that returned `null`.

An exception from the embedding model, such as `EmbeddingRateLimitException`, propagates unwrapped.

## Semantic cache

Java implementation of Valkey or Redis based [RSemanticCache](https://static.javadoc.io/org.redisson/redisson-ai-core/latest/org/redisson/api/RSemanticCache.html) object caches model answers and looks them up by what a prompt means rather than by its exact text. "What is the refund policy?" and "How do refunds work?" can hit the same entry, so the second question costs an embedding call and a vector search instead of a chat-model call.

An answer produced from retrieved documents goes stale when one of those documents changes, even though no prompt has changed. So each entry can record the ids of the documents it was derived from, and `invalidateByDependency` removes exactly the answers built from a changed document.

Entries live in an ordinary [vector store](#vector-store) of `CachePayload`, whose filterable schema must declare `SemanticCacheOptions.REQUIRED_SCHEMA` and whose metric must be cosine. Several caches can share one store.

=== "Sync"
	```java
	RVectorStore<CachePayload> store = RedissonAI.of(redisson).getVectorStore(
	        RediSearchStoreOptions.hnsw("answers")
	                .payloadType(CachePayload.class)
	                .payloadCodec(new JacksonCodec<>(CachePayload.class))
	                .embeddingModel(model)
	                .filterableSchema(SemanticCacheOptions.REQUIRED_SCHEMA));

	RSemanticCache cache = RedissonAI.of(redisson).getSemanticCache(
	        SemanticCacheOptions.name("support")
	                .store(store)
	                .threshold(0.95)
	                .ttl(Duration.ofHours(6)));

	Optional<CacheHit> hit = cache.lookup(prompt);
	String answer;
	if (hit.isPresent()) {
	    answer = hit.get().response();
	} else {
	    answer = chatModel.call(prompt);
	    cache.put(CacheEntry.of(prompt, answer).dependencies("policy-3", "policy-7"));
	}

	// policy-7 was edited: every answer built from it goes, and nothing else does
	cache.invalidateByDependency(List.of("policy-7"));
	```

=== "Async"
	```java
	RSemanticCacheAsync cache = RedissonAI.of(redisson).getSemanticCache(
	        SemanticCacheOptions.name("support").store(store).threshold(0.95));

	RFuture<CacheHit> hit = cache.lookupAsync(prompt);   // completes with null on a miss
	RFuture<String> id = cache.putAsync(CacheEntry.of(prompt, answer).dependencies("policy-3"));
	RFuture<InvalidationResult> removed = cache.invalidateByDependencyAsync(List.of("policy-3"));
	```

=== "Reactive"
	```java
	RSemanticCacheReactive cache = RedissonAI.of(redisson.reactive()).getSemanticCache(
	        SemanticCacheOptions.name("support").store(store).threshold(0.95));

	Mono<CacheHit> hit = cache.lookup(prompt);            // empty on a miss
	Mono<String> id = cache.put(CacheEntry.of(prompt, answer).dependencies("policy-3"));
	Mono<InvalidationResult> removed = cache.invalidateByDependency(List.of("policy-3"));
	```

=== "RxJava3"
	```java
	RSemanticCacheRx cache = RedissonAI.of(redisson.rxJava()).getSemanticCache(
	        SemanticCacheOptions.name("support").store(store).threshold(0.95));

	Maybe<CacheHit> hit = cache.lookup(prompt);           // empty on a miss
	Single<String> id = cache.put(CacheEntry.of(prompt, answer).dependencies("policy-3"));
	Single<InvalidationResult> removed = cache.invalidateByDependency(List.of("policy-3"));
	```

The store for the reactive and RxJava3 caches is an ordinary blocking `RVectorStore`. Construction checks the store's schema and metric, and embeds one fixed probe text to fingerprint the model, so it costs one model call.

| Option | Description | Default Value |
|---|---|---|
| `name` | Cache name, matching `[A-Za-z0-9._-]+`. Scopes every lookup and invalidation | required |
| `store` | The `RVectorStore<CachePayload>` entries live in | required |
| `threshold` | Minimum normalized similarity, in `[0, 1]`, for a stored prompt to be served | `0.9` |
| `ttl` | How long an entry stays servable | no expiration |
| `normalisePrompt` | Trim the prompt and collapse internal whitespace before hashing and embedding. The stored prompt keeps the caller's exact text | `true` |
| `refreshTtlOnHit` | Whether a hit restarts the entry's lifetime, advancing both its expiry and `storedAt()`. Makes every hit a write | `false` |
| `meterName` | Value of the `cache` tag on the cache's meters | the cache name |

Leave `refreshTtlOnHit` off for anything retrieval-augmented: the TTL is the backstop for answers whose dependencies were not recorded, and refreshing it on every hit would keep a popular stale answer alive forever. Turn it on for answers that cannot go stale, such as classification or translation over a fixed corpus.

### Looking up

`lookup(prompt)` returns the best entry at or above the cache's threshold. `lookup(CacheQuery)` overrides the threshold, the number of candidates ranked, or narrows the lookup with a filter, for one call:

```java
Optional<CacheHit> hit = cache.lookup(CacheQuery.of("what is the status of order 41822")
        .threshold(0.99)                             // this question tolerates no paraphrase
        .filter(VectorFilter.eq("tenant", "acme")));

hit.ifPresent(h -> {
    h.response();       // the cached answer
    h.prompt();         // the prompt it was stored under - not the one asked
    h.askedPrompt();    // the prompt of this lookup
    h.similarity();     // how close the two are
    h.threshold();      // the bar it cleared
    h.dependencies();   // the document ids it was derived from
    h.metadata();
    h.storedAt();
    h.expiresAt();
});
```

A hit answers a different question from the one asked, and says so: `prompt()` and `similarity()` are what make a wrong hit debuggable.

A query filter is added to the cache's own scope - this cache, the current model's fingerprint and unexpired entries - and can only narrow it. It can name only fields declared in the store's schema, and it matches only entries written with them.

### Storing answers

```java
String id = cache.put(CacheEntry.of(prompt, answer)
        .dependencies(retrievedDocumentIds)           // what the answer was built from
        .filterable("tenant", "acme")                 // what you may need to invalidate by later
        .metadata(Map.of("model", "gpt-5"))           // kept with the answer, not queryable
        .ttl(Duration.ofMinutes(30)));                // overrides the cache's ttl, in either direction

List<String> ids = cache.putAll(entries);             // one batched embedding pass, not a call per entry
```

- **The entry id is derived from the cache name, the model fingerprint and the normalized prompt**, so storing the same prompt again overwrites the previous entry, and a retried `put` leaves one entry.
- **An empty answer is an answer.** `CacheEntry.of(prompt, "")` is cached and later hits.
- **Dependencies cannot be inferred.** Only the retrieval step knows which documents it used. An entry stored without dependencies can be invalidated only by its exact prompt, a filter on its own fields, `invalidateAll()`, or its TTL.
- **A custom filterable field must be declared in the store's schema** alongside the required fields. The five field names the cache uses - `cache`, `fp`, `dep`, `exp` and `writtenAt` - are refused.

```java
Map<String, FieldType> schema = new HashMap<>(SemanticCacheOptions.REQUIRED_SCHEMA);
schema.put("tenant", FieldType.TAG);

RVectorStore<CachePayload> store = RedissonAI.of(redisson).getVectorStore(
        RediSearchStoreOptions.hnsw("answers")
                .payloadType(CachePayload.class)
                .payloadCodec(new JacksonCodec<>(CachePayload.class))
                .embeddingModel(model)
                .filterableSchema(schema));
```

There is no stampede protection: callers that miss on the same cold prompt at the same moment all call the model, and one of their entries survives. The cost is money, not correctness.

### Invalidation

| Method | Removes |
|---|---|
| `invalidate(prompt)` | The entry for exactly this prompt, after normalization. A paraphrase does not match |
| `invalidateByDependency(documentIds)` | Every entry derived from any of the documents. An empty collection throws rather than doing nothing |
| `invalidateByFilter(filter)` | Every entry matching a filter over your own fields, such as a tenant or a prompt-template version. An unbounded filter is refused |
| `invalidateAll()` | Every entry of this cache, and nothing belonging to another cache in the same store |
| `sweepExpired()` | Entries whose expiry has passed |

`invalidate` returns whether an entry existed and `sweepExpired` returns how many entries it removed; the other three return an `InvalidationResult` with what matched and what was removed. Two methods rehearse an invalidation without removing anything:

```java
// which answers would invalidateByDependency(List.of("policy-7")) take?
List<CacheHit> derived = cache.dependents("policy-7", 50);

// how many records would this filter remove?
InvalidationResult rehearsal = cache.preview(VectorFilter.eq("tenant", "acme"));
rehearsal.matched();
```

**Schedule `sweepExpired()`.** Expiry is logical on both backends: every lookup skips expired entries, but the store never sets a Redis TTL on an entry, so `sweepExpired()` is the only thing that frees the memory of an expired entry. An entry with no TTL is never swept.

Everything except `invalidate(prompt)` walks the store - the other invalidations, `sweepExpired`, `preview` and `dependents` - so it needs `supportsEnumeration()`: Redis 8.4+ on vector sets, and unavailable on `valkey-search`. Without it these methods throw `UnsupportedByBackendException`.

### Choosing a threshold

There is no universally right threshold, and the default of `0.9` is a placeholder. The two errors are not symmetric: a false miss costs one model call, while a false hit answers a question nobody asked and the caller cannot tell. Label a set of prompts with the entry each should hit, then use `lookupAll`, which returns up to `k` candidates (5 by default) that clear the query's threshold, each with its similarity, and moves no counter, to see what each threshold would serve:

```java
List<CacheHit> candidates = cache.lookupAll(CacheQuery.of(prompt).threshold(0).k(5));
for (CacheHit c : candidates) {
    System.out.printf("%.4f  %s%n", c.similarity().getAsDouble(), c.prompt());
}
```

Optimise for precision. `threshold(1.0)` does not mean exact match; it means never hit, because the similarity of a prompt with itself comes back from a `float32` search at about `0.9999999`. Use `0.999` to serve only prompts seen verbatim, for example on a first deployment.

### Model changes

Every entry carries a fingerprint of how the embedding model embeds a fixed probe text, and every lookup filters on the current fingerprint. After the model changes, old entries stop matching, so the change shows up as a run of misses and never as a wrong answer. They remain reachable by `invalidateAll()`, `invalidateByDependency()` and `sweepExpired()`, but not by `invalidate(prompt)`, whose id includes the fingerprint.

### Statistics

`stats()` returns the counters of this cache object - each `getSemanticCache` call returns a new one - for lookups, hits, misses, near misses (misses where candidates existed below the threshold), expired skips, puts, overwrites, invalidations and swept entries. `resetStats()` zeroes them; the Micrometer meters are not reset.

A non-zero `corrupt()` count means matching records came back without a payload: either something other than the cache wrote records into its store, or a payload read failed or could not be decoded, for example after a `payloadCodec` change. `redisson.vectorstore.payload.errors` tells the two apart. Those lookups report a miss, not an error.

## Agent memory

Java implementation of Valkey or Redis based [RAgentMemory](https://static.javadoc.io/org.redisson/redisson-ai-core/latest/org/redisson/api/RAgentMemory.html) object holds an agent's conversations: each is ordered, bounded to a window of recent turns, expiring, and appended atomically. Turns that fall out of the window can be archived to a [vector store](#vector-store) and searched later by meaning.

=== "Sync"
	```java
	RAgentMemory memory = RedissonAI.of(redisson).getAgentMemory(
	        MemoryOptions.name("support")
	                .messageCodec(new JacksonCodec<>(MemoryMessage.class))
	                .maxMessages(20)
	                .ttl(Duration.ofHours(2)));

	memory.append("case-8813", new MemoryMessage(Role.SYSTEM, "You are a billing assistant."));
	memory.append("case-8813", new MemoryMessage(Role.USER, "My invoice address is wrong"));

	List<MemoryMessage> window = memory.messages("case-8813");
	List<MemoryMessage> recent = memory.lastMessages("case-8813", 5);
	```

=== "Async"
	```java
	RAgentMemoryAsync memory = RedissonAI.of(redisson).getAgentMemory(
	        MemoryOptions.name("support")
	                .messageCodec(new JacksonCodec<>(MemoryMessage.class))
	                .maxMessages(20));

	RFuture<Long> size = memory.appendAsync("case-8813",
	        new MemoryMessage(Role.USER, "My invoice address is wrong"));
	RFuture<List<MemoryMessage>> window = memory.messagesAsync("case-8813");
	```

=== "Reactive"
	```java
	RAgentMemoryReactive memory = RedissonAI.of(redisson.reactive()).getAgentMemory(
	        MemoryOptions.name("support")
	                .messageCodec(new JacksonCodec<>(MemoryMessage.class))
	                .maxMessages(20));

	Mono<Long> size = memory.append("case-8813",
	        new MemoryMessage(Role.USER, "My invoice address is wrong"));
	Mono<List<MemoryMessage>> window = memory.messages("case-8813");
	Mono<ConversationInfo> info = memory.info("case-8813");   // empty when absent
	Flux<String> ids = memory.conversationIds();
	```

=== "RxJava3"
	```java
	RAgentMemoryRx memory = RedissonAI.of(redisson.rxJava()).getAgentMemory(
	        MemoryOptions.name("support")
	                .messageCodec(new JacksonCodec<>(MemoryMessage.class))
	                .maxMessages(20));

	Single<Long> size = memory.append("case-8813",
	        new MemoryMessage(Role.USER, "My invoice address is wrong"));
	Single<List<MemoryMessage>> window = memory.messages("case-8813");
	Maybe<ConversationInfo> info = memory.info("case-8813");  // empty when absent
	Flowable<String> ids = memory.conversationIds();
	```

On the synchronous and async faces, a null message or an invalid conversation id throws on the calling thread. The Reactive and RxJava3 faces call the async method only when subscribed, so the same mistake arrives as an error at subscription, with the `NullPointerException` or `IllegalArgumentException` as its root cause.

Construction does blocking I/O: it checks the archive's schema and has the server's own Lua `cjson` read back a turn encoded by `messageCodec`, so a codec that does not produce JSON fails at wiring time.

| Option | Description | Default Value |
|---|---|---|
| `name` | Memory name, matching `[A-Za-z0-9._-]+` | required |
| `messageCodec` | `JsonCodec` for stored turns. Required, and must produce JSON: the append script reads each turn's `role` on the server. Checked at construction | required |
| `maxMessages` | The window: how many turns a conversation keeps. Applied inside the write; `0` is unbounded | `0` |
| `maxChars` | The budget: how much of the window a read returns, oldest turns dropped first - a `SYSTEM` message included. Applied on read and deletes nothing by itself, but see [Appending and replacing](#appending-and-replacing) for the framework adapters; `0` is unbounded | `0` |
| `sizeFunction` | What `maxChars` counts. Plug a tokenizer in here for a budget in tokens | characters of text and tool calls |
| `ttl` | Per-conversation expiry, refreshed on every write | no expiration |
| `refreshTtlOnRead` | Whether reads also refresh the expiry | `false` |
| `preserveSystemMessages` | Never evict a `SYSTEM` message, and let a newer one replace the older - as Spring AI's window does | `true` |
| `archive` | An `RVectorStore<TextPayload>` that evicted turns are written to | none: evicted turns are dropped |
| `meterName` | Value of the `memory` tag on the memory's meters | the memory name |

There is no `getAgentMemory(String name)` shortcut, because the window, the expiry and the archive have to be decided before the first append.

### Messages

A `MemoryMessage` has a role - `SYSTEM`, `USER`, `ASSISTANT`, `TOOL` or `CUSTOM` - text, a creation time, metadata and tool calls:

```java
memory.append("case-8813", new MemoryMessage(Role.ASSISTANT, null, null,
        Map.of("model", "gpt-5"),
        List.of(new ToolCall("call-1", "lookupInvoice", "{\"id\":\"INV-204\"}"))));
```

The creation time is stamped on append when left `null`. Metadata keys beginning with `mem.` are reserved and refused. The list a read returns, and each message's metadata and tool calls, are unmodifiable snapshots.

### Appending and replacing

`append` and `appendAll` add turns in one atomic script that also applies the window, updates the conversation's metadata and stages evicted turns for the archive. Concurrent appends from several threads or services never lose a turn.

`replaceAll` overwrites the whole window, which is what the Spring AI and LangChain4j memory interfaces require: their only write takes the entire conversation, so adding one message through them is a read-modify-write. `replaceAll` is not atomic against a concurrent writer. Measured with eight threads adding sixty messages each:

| Path | Messages kept, of 480 |
|---|---|
| `RAgentMemory.append` | 480 |
| `saveAll` read-modify-write through the Spring AI adapter | 70-289 |
| `saveAll` through Spring AI's own `InMemoryChatMemoryRepository` | 137-480 |

The loss belongs to the framework interface, not to this implementation. **Append from the agent's own turn loop, and use the framework adapters as the read and compatibility path.** `replaceAll` does not archive the turns it drops, and an empty list empties the window without deleting the conversation.

Two consequences for a memory used only through a framework adapter, because every framework write is a `replaceAll`:

- **Nothing reaches the archive.** Turns the framework's window drops are deleted, not archived.
- **`maxChars` deletes turns.** The adapter reads through the budget and the framework writes back what it read, so turns the budget hid are removed on the next add. Measured with `maxMessages(3)` and `maxChars(20)` through Spring AI's `MessageWindowChatMemory`: after five adds the window held two turns, a read returned one, and the archive held none.

### Reading

| Method | Returns |
|---|---|
| `messages(id)` | The window, oldest first, within the `maxChars` budget. Empty for an unknown id |
| `lastMessages(id, n)` | The newest `n` turns, oldest first, within the budget |
| `size(id)` | Turns in the window. Can exceed `messages(id).size()`, because the budget applies on read |
| `exists(id)` | Whether the conversation exists. Exact |
| `info(id)` | Creation and last-write times, and the live, archived and pending counts. Empty once the conversation expires |
| `conversationIds()` | Every conversation, approximately: a walk may miss one created during it or include one that has just expired |

Use `exists` for any decision that depends on whether a conversation is there. `conversationIds()` is for administrative sweeps.

### Archive and recall

An archive is an ordinary vector store of `TextPayload`. Its filterable schema must declare `mem`, `conv` and `role` as `TAG` and `createdAt` as `NUMERIC`; construction refuses an archive that does not.

```java
RVectorStore<TextPayload> archive = RedissonAI.of(redisson).getVectorStore(
        RediSearchStoreOptions.hnsw("memory-archive")
                .payloadType(TextPayload.class)
                .payloadCodec(new JacksonCodec<>(TextPayload.class))
                .embeddingModel(model)
                .filterableSchema(Map.of(
                        "mem", FieldType.TAG,
                        "conv", FieldType.TAG,
                        "role", FieldType.TAG,
                        "createdAt", FieldType.NUMERIC)));

RAgentMemory memory = RedissonAI.of(redisson).getAgentMemory(
        MemoryOptions.name("support")
                .messageCodec(new JacksonCodec<>(MemoryMessage.class))
                .maxMessages(20)
                .archive(archive));

// semantic search over this conversation's archived turns
List<MemoryMatch> older = memory.recall("case-8813", "invoice address", 3);

// archive everything but the newest 5 turns now, a SYSTEM message included
ArchiveResult moved = memory.archive("case-8813", 5);
```

Because the archive is a normal store, archived turns are also readable through the Spring AI vector store adapter. Through the LangChain4j adapter, a search that returns a turn with tool calls, or with a `Boolean` or list metadata value such as LangChain4j's own `lc4j.isError`, throws `IllegalArgumentException`, because LangChain4j metadata cannot hold those values. One archive can serve several memories.

**Eviction is crash-safe.** An evicted turn is staged in an outbox in the same atomic call that evicts it, and written to the archive afterwards. A crash in between leaves the turn staged, not lost, and a retried archive write updates the same record rather than duplicating it. If the archive store fails, the append still succeeds: the turns stay staged and the next append to that conversation retries them. Staged turns share the conversation's `ttl`, so they expire with it if nothing appends to it or drains it first. An append that evicts waits for the archive write, including its embedding call. `pendingCount(id)` is the outbox depth and `drain(id)` archives what is staged. **A pending count that is non-zero and not falling is the alert worth paging on** - turns are leaving the window without reaching the archive.

`recall` returns the best `k` archived turns with no similarity threshold, and throws `IllegalStateException` on a memory without an archive. `archive(id, keep)` on a memory without an archive does nothing and reports `hasArchive() == false`.

### Retention and erasure

| Setting or method | Bounds |
|---|---|
| `maxMessages` | The live window, on every write |
| `maxChars` | What a read returns; deletes nothing by itself |
| `ttl` | The conversation's live keys. Cannot reach the archive: after it fires, `exists` is `false` and `recall` still answers |
| `clear(id)` | Erases one conversation: window, metadata, outbox **and archived turns** |
| `evictInactiveSince(cutoff)` | Erases every conversation not written since `cutoff`, archive included. The only bound on the archive's total size |

With both a `ttl` and an archive, run `evictInactiveSince` with an inactivity period shorter than the `ttl` - a cutoff later than now minus the `ttl` - or leave the `ttl` unset. The sweep finds conversations through their metadata, which the `ttl` deletes, so archived turns of a conversation the `ttl` reached first can no longer be found by the sweep.

On a memory with an archive, `clear` and `evictInactiveSince` delete archived turns with `deleteByFilter`, so they need the archive store's `supportsEnumeration()`: Redis 8.4+ on vector sets, and unavailable on `valkey-search`.

### Keys and conversation ids

Each conversation is stored in three keys that share a Redis Cluster hash tag, so one script can update them atomically:

```
mem:<memory>:{<conversationId>}:log       the window, a list, oldest first
mem:<memory>:{<conversationId>}:meta      created, last write, sequence, counters
mem:<memory>:{<conversationId>}:pending   the archive outbox
```

A conversation id must be non-empty, contain no NUL byte, and **contain no brace**: a brace would change which slot the keys land in and silently break the atomicity of an append. A composite id such as `{tenant}:{user}` is the shape that breaks - use `tenant:user`.

Every read of the memory, including `size`, `exists` and `pendingCount`, goes to the master, because each of them is compared with a value the master just wrote.

## Spring AI integration

The `redisson-store-spring-ai` module exposes the vector store, the agent memory and the semantic cache through Spring AI's own interfaces. It requires Spring AI 2.0 and does not bring it: declare `spring-ai-vector-store`, `spring-ai-model` and `spring-ai-client-chat` in your build.

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>redisson-store-spring-ai</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
implementation 'pro.redisson:redisson-store-spring-ai:xVERSIONx'
```

### VectorStore

`org.redisson.spring.ai.RedissonVectorStore` implements Spring AI's `VectorStore` over an `RVectorStore<TextPayload>`. The document text is stored in the `TextPayload`, and embedding is done by the Redisson store's own model rather than by a Spring AI `EmbeddingModel`.

```java
RVectorStore<TextPayload> backing = RedissonAI.of(redisson).getVectorStore(
        RediSearchStoreOptions.hnsw("kb")
                .payloadType(TextPayload.class)
                .payloadCodec(new JacksonCodec<>(TextPayload.class))
                .embeddingModel(model)
                .filterableSchema(Map.of("topic", FieldType.TAG, "year", FieldType.NUMERIC)));

VectorStore vectorStore = new RedissonVectorStore(backing, false,
        RedissonAI.of(redisson).getMeterRegistry());

vectorStore.add(List.of(Document.builder()
        .id("a")
        .text("Redis vector sets are a native index")
        .metadata(Map.of("topic", "redis", "year", 2025, "author", "docs"))
        .build()));

List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
        .query("vector index")
        .topK(5)
        .similarityThreshold(0.7)
        .filterExpression("topic == 'redis' && year >= 2024")
        .build());
```

Metadata keys declared in the store's `filterableSchema` - `topic` and `year` above - are stored as filterable attributes and can be used in filter expressions. Other keys, such as `author`, travel in the payload and cannot be filtered on; they come back as the payload codec decodes them, so with JSON a `Long` may return as an `Integer`, a `Float` as a `Double` and a `UUID` as a `String`. Filter expressions are translated into [VectorFilter](#filtering) trees, so values are escaped and the same expression works on both backends. A filter naming an undeclared, `TEXT` or `GEO` key throws `IllegalArgumentException`.

The second constructor argument, `allowUnboundedDelete`, permits `delete(Filter.Expression)` with a filter that does not restrict any field to a finite set of values. Leave it `false`: Spring AI has no notion of a bounded filter, and a stray `!=` would otherwise empty the store.

**Differences from Spring AI's reference store** that a migration meets:

- **Scores use a different scale.** `Document.getScore()` is the normalized similarity `(1 + cos) / 2`, not the raw cosine, and `similarityThreshold` applies on that scale. A threshold of `0.70` keeps `cos >= 0.40` here and `cos >= 0.70` in Spring AI's `SimpleVectorStore`, so the same number is looser here.
- **The missing-field rule applies.** `!=`, `NOT`, `OR` and `NIN` over a field some documents lack match fewer documents than in Spring AI's in-memory evaluator, never more.
- **Filterable values come back as stored types.** A `Boolean` in a filterable field comes back as the `String` `"true"`; any integral number comes back as a `Long` and any other number as a `Double`; a list written through the Redisson API comes back comma-joined.
- **The metadata key `distance` is reserved.** Every result carries `distance = 1 - score`, that is `(1 - cos) / 2`, half the value `SimpleVectorStore` reports. `add` refuses a document carrying the key, so remove it before writing a search result back.
- **Some documents and schemas are refused**: a document with blank text or with media (`IllegalArgumentException`), and a schema key outside `[A-Za-z0-9_-]` or named `distance`, which makes the adapter's constructor throw `IllegalStateException`.
- **`delete(String)` is not delete-by-id.** As in every Spring AI store, it parses its argument as a filter expression. Use `delete(List<String>)` to delete by id.
- **A store with a non-cosine metric** returns documents without a score and refuses a `similarityThreshold`.
- **There are no `db.vector.*` observations**, because the adapter does not extend Spring AI's observation base class.

### ChatMemoryRepository

`RedissonChatMemoryRepository` implements `ChatMemoryRepository` over an [agent memory](#agent-memory):

```java
RAgentMemory memory = RedissonAI.of(redisson).getAgentMemory(
        MemoryOptions.name("support")
                .messageCodec(new JacksonCodec<>(MemoryMessage.class))
                .maxMessages(20));

ChatMemoryRepository repository = new RedissonChatMemoryRepository(memory);
ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(20)
        .build();
```

`saveAll` is a read-modify-write, as it is in every implementation of the interface, so concurrent writers can lose messages - see [Appending and replacing](#appending-and-replacing). Where that matters, append with `RAgentMemory.append` and use the repository for reads.

What changes when you swap in this repository:

| | In-memory or JDBC repository | This repository |
|---|---|---|
| Conversation ids containing `{` or `}` | Accepted | `IllegalArgumentException`. Use `tenant:user` rather than `{tenant}:{user}` |
| `findConversationIds()` | Exact | Approximate. Use `RAgentMemory.exists(id)` when the answer matters |
| A multi-part `UserMessage`, or an `AssistantMessage` with media | Stored | `UnsupportedOperationException` |
| A `CUSTOM` turn written through LangChain4j, read here | - | `UnsupportedOperationException` |
| Metadata keys beginning with `mem.` | Stored | `IllegalArgumentException` |
| The window | Applied by `MessageWindowChatMemory` | Applied again by the memory, and the smaller of the two wins. Set `maxMessages` to match |
| The archive | - | Turns dropped through `saveAll` are never archived |
| `maxChars` | - | Turns the budget hides on read are deleted on the next add |
| Expiry | Conversations live until deleted | A `ttl` expires them |

Unchanged: an unknown id reads as an empty list, `saveAll` replaces the window, an empty `saveAll` empties the window without deleting the conversation, and `deleteByConversationId` deletes the conversation and its archived turns.

### Semantic cache advisor

`SemanticCacheAdvisor` puts a [semantic cache](#semantic-cache) into a `ChatClient` advisor chain. On a hit it returns the cached answer and never calls the model; on a miss it calls the rest of the chain and caches the answer.

```java
ChatClient client = ChatClient.builder(chatModel)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new SemanticCacheAdvisor(cache))
        .build();

ChatClientResponse response = client.prompt()
        .user("what is the refund policy?")
        .advisors(a -> a.param(Advisors.DEPENDENCIES_CONTEXT_KEY, List.of("policy-3", "policy-7")))
        .call()
        .chatClientResponse();

CacheHit hit = (CacheHit) response.context().get(Advisors.HIT_CONTEXT_KEY);   // null on a miss
```

- **The key is the prompt as finally assembled**: the role and text of every message in it, system prompt included, so answers produced under different system prompts are kept apart. The default order places the advisor after Spring AI's chat-memory advisors for that reason; `new SemanticCacheAdvisor(cache, order)` sets another.
- **Chat options are not part of the key.** Requests that differ only in model, temperature, tools or response format share entries; give them separate caches.
- **Dependencies come from the request context** under `Advisors.DEPENDENCIES_CONTEXT_KEY`, set per call as above or by your own retrieval advisor, ordered before this one, that puts the retrieved ids there. Spring AI's own retrieval advisors do not set this key. Dependencies are what make [invalidation by dependency](#invalidation) work.
- **A hit is announced** in the response context under `Advisors.HIT_CONTEXT_KEY`, so a cached answer can be told apart from a generated one. The hit's response carries only the answer text, with no usage or other metadata.
- **A response without answer text, such as a tool call, is not cached.**
- **Streaming is not cached.** The advisor is a `CallAdvisor`; a streamed request passes through it untouched.
- **It does not fail open.** If Redis or the embedding provider is unavailable, the lookup throws and so does the chat call; if storing fails after a miss, the call throws and the model's answer is lost.

`new SemanticCacheAdvisor(cache, order, threshold, filter)` overrides the cache's threshold and adds a filter to every lookup the advisor makes. The advisor stores its entries without filterable fields, so a filter on your own field never matches what the advisor itself stored; use it only when the entries it should find are written elsewhere with `CacheEntry.filterable(...)`.

### Spring AI embedding bridge

`redisson-embedding-springai` wraps a Spring AI `EmbeddingModel` as an `REmbeddingModel`, and the reverse, as shown in [Providers](#providers). It is compiled against Spring AI 1.0.0 and runs unchanged on 2.0.0, so it can share an application with `redisson-store-spring-ai` on Spring AI 2.0.0. Add `spring-ai-model` and `spring-ai-commons` to your build; the module does not bring them.

## LangChain4j integration

The `redisson-store-langchain4j` module exposes the vector store, the agent memory and the semantic cache through LangChain4j's interfaces. It is compiled against `dev.langchain4j:langchain4j-core` 1.18.1, runs on 1.18.x, and does not bring it: declare a 1.18.x version in your build.

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>redisson-store-langchain4j</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
implementation 'pro.redisson:redisson-store-langchain4j:xVERSIONx'
```

### EmbeddingStore

`RedissonEmbeddingStore` implements `EmbeddingStore<TextSegment>` over an `RVectorStore<TextPayload>`:

```java
EmbeddingStore<TextSegment> store = new RedissonEmbeddingStore(backing, false,
        RedissonAI.of(redisson).getMeterRegistry());

Embedding embedding = embeddingModel.embed("Redis vector sets are a native index").content();
store.add(embedding, TextSegment.from("Redis vector sets are a native index",
        Metadata.from(Map.of("topic", "redis", "year", 2025))));

EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
        .queryEmbedding(embeddingModel.embed("vector index").content())
        .maxResults(5)
        .minScore(0.7)
        .filter(metadataKey("topic").isEqualTo("redis"))
        .build());
```

**In LangChain4j the caller embeds.** Every write and search arrives with a vector already computed, and the Redisson store's own embedding model is never called on this path. The adapter checks each vector's width against the store, but it cannot detect a different model of the same width, which produces plausible, wrong rankings. Use the same model for the LangChain4j side and the store's `embeddingModel`.

- **Scores** are the normalized similarity, which is also LangChain4j's `RelevanceScore.fromCosineSimilarity`. A store with a non-cosine metric cannot be searched through this adapter, because LangChain4j requires a score on every match.
- **The missing-field rule applies**: `IsNotEqualTo`, `Not`, `Or` and `IsNotIn` over a field some segments lack match fewer segments than `Filter.test` does. Re-applying the same filter client-side to the results can therefore keep more rows than the server returned.
- **A filterable boolean comes back as a string.** LangChain4j metadata cannot hold a `Boolean`, but a record written through the Redisson API can, and it reads back as `"true"`. A second-pass `new IsEqualTo("flag", true).test(...)` on such a result throws; compare against `"true"` client-side.
- **Metadata LangChain4j cannot hold breaks a search.** A record whose metadata contains a `Boolean` outside the filterable schema, a list or a map - legal through the Redisson API or the Spring AI adapter - makes any search that returns it throw `IllegalArgumentException`.
- **`EmbeddingMatch.embedding()` is always `null`**, because a vector read back from a vector set is only approximate.
- **The metadata key `distance` is reserved** here too, and refused on write.
- **`removeAll()`** removes every segment the store can see - on a store with a filter policy, only the current tenant's.

### ChatMemoryStore

`RedissonChatMemoryStore` implements `ChatMemoryStore` over an [agent memory](#agent-memory):

```java
ChatMemoryStore chatMemoryStore = new RedissonChatMemoryStore(memory);

ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryStore(chatMemoryStore)
        .maxMessages(20)
        .id("case-8813")
        .build();
```

`updateMessages` replaces the whole window, so adding a message through `ChatMemory` is a read-modify-write, as with every implementation of the interface; append with `RAgentMemory.append` where concurrent writers matter. As with the Spring AI repository, turns dropped this way are never archived, and `maxChars` deletes the turns it hides. The memory id reaches Redis as its `toString()`, so `5` and `"5"` are the same conversation, and it must not contain a brace. `deleteMessages` deletes the conversation and its archived turns.

Refused with `UnsupportedOperationException`: a multi-content `UserMessage`, a message with non-empty `attributes()` other than a `CustomMessage`, and, on reading back, a `TOOL` turn with no tool id or name or with several tool responses - which is what Spring AI writes.

### Caching chat model

LangChain4j has no hook that can answer instead of the model, so the semantic cache is a `ChatModel` decorator:

```java
ChatModel cached = new CachingChatModel(chatModel, cache);

// with dependencies from your retrieval step
ChatModel cachedWithDependencies = new CachingChatModel(chatModel, cache,
        () -> currentRetrieval().stream().map(Match::id).toList());

String answer = cached.chat("what is the refund policy?");
Optional<CacheHit> hit = ((CachingChatModel) cached).lastHit();   // the last call on this thread through any CachingChatModel
```

On a hit the delegate is never called, so the delegate's listeners, retries and observability do not run, and the returned response carries only the answer text. On a miss the delegate is called through its public `chat` method, so they run as usual. The key is the whole conversation in the request, so two conversations that end with the same words do not share an answer. Chat options - model, temperature, tools, response format - are not part of the key.

A request the decorator cannot render as text bypasses the cache entirely: one containing an image or other media, a `CustomMessage` with attributes, or an AI tool-call message anywhere in its history. In a tool-using agent that is every request after the first tool call. A response that is a tool call rather than an answer is not cached. Like the Spring AI advisor, the decorator does not fail open: a cache or embedding failure fails the chat call.

`new CachingChatModel(delegate, cache, dependencies, threshold, filter)` overrides the threshold and adds a filter to every lookup. The decorator stores its entries without filterable fields, so a filter on your own field matches only entries written elsewhere with `CacheEntry.filterable(...)`.

### LangChain4j embedding bridge

`redisson-embedding-langchain4j` wraps a LangChain4j `EmbeddingModel` as an `REmbeddingModel`, and the reverse, as shown in [Providers](#providers).

## Metrics

Every Redisson AI object reports Micrometer meters into Redisson's shared registry:

| Meter | Type | Tags |
|---|---|---|
| `gen_ai.client.operation` | timer: call count and latency of provider requests | `gen_ai.operation.name`, `gen_ai.system`, `gen_ai.request.model`, `gen_ai.response.model` |
| `gen_ai.client.token.usage` | counter | the above, plus `gen_ai.token.type` |
| `redisson.embedding.provider.texts` | counter: texts sent to the provider | `provider`, `model` |
| `redisson.embedding.provider.errors` | counter | `provider`, `model`, `outcome` |
| `redisson.embedding.provider.retries` | counter | `provider`, `model` |
| `redisson.embedding.batch.chunks` | counter: chunks the batching strategy produced, before retries and splits | `provider`, `model` |
| `redisson.embedding.batch.oversized` | counter: batches halved after a token-limit error | `provider`, `model` |
| `redisson.embedding.cache.hits`, `.misses`, `.puts`, `.errors` | counter | `cache`, `model` |
| `redisson.vectorstore.search` | timer | `store`, `backend` |
| `redisson.vectorstore.search.results` | distribution summary: results per search | `store`, `backend` |
| `redisson.vectorstore.upsert`, `redisson.vectorstore.upsert.failed` | timer, counter | `store`, `backend` |
| `redisson.vectorstore.payload.reads`, `.payload.errors` | counter | `store`, `backend`, and `projected` on reads |
| `redisson.vectorstore.filter.rejected` | counter: filters refused before sending | `store`, `backend`, `reason` |
| `redisson.vectorstore.filter.unbounded` | counter: unbounded filters used | `store`, `backend` |
| `redisson.vectorstore.filter.policy.shadowed` | counter: caller filters naming a policy field | `store`, `backend` |
| `redisson.vectorstore.filter.clientside` | counter: thresholds applied client-side because the server could not apply them | `store`, `backend` |
| `redisson.vectorstore.cluster.dispatch.failed` | counter: Query Engine searches that failed to reach one or more shards | `store`, `backend` |
| `redisson.cache.lookup` | timer | `cache`, `fp` |
| `redisson.cache.hit`, `.miss`, `.nearMiss`, `.expired`, `.corrupt` | counter | `cache`, `fp` |
| `redisson.cache.invalidated` | counter | `cache`, `fp`, `reason` |
| `redisson.memory.append`, `redisson.memory.recall` | timer | `memory` |
| `redisson.memory.evicted` | counter: turns removed or left out, by reason: `window`, `replaced`, `superseded`, `sweep`, `unarchivable`, `budget`. `budget` counts turns a read left out, again on every read; nothing is deleted | `memory`, `reason` |
| `redisson.memory.archived` | counter | `memory` |
| `redisson.memory.pending`, `redisson.memory.conversations` | gauge: outbox depth summed over all conversations, and the conversation count. Recomputed at most every 10 seconds by a keyspace walk when read | `memory` |
| `redisson.ai.adapter.filter.rejected` | counter: framework filters that could not be translated | `framework`, `reason` |

Provider meters use the OpenTelemetry GenAI attribute names, as a Micrometer timer and counter. `gen_ai.token.type` is `input` or `total`, so do not sum across types, and `gen_ai.response.model` is the configured model id rather than the one the provider reported. Per-request values such as token counts are meter values, never tags.

Several readings are only meaningful in pairs:

| Reading | Likely cause | What to do |
|---|---|---|
| `gen_ai.client.operation` count close to `provider.texts` | Batching is not working: one request per text | Check `batchSize` and where texts are embedded one at a time |
| `batch.oversized` non-zero and steady | The token estimate is too low for this corpus | Supply a real tokenizer to `TokenBudgetBatchingStrategy`, or raise its reserve ratio |
| `embedding.cache.errors` rising, provider latency unchanged | Redis is degraded and the cache is failing open, as designed | Not an outage, but a cost event: provider spend is no longer reduced by the cache |
| `embedding.cache.hits` low on a stable corpus | Cache keys differ between clients: `normalization`, `defaultOptions`, a per-call `dimensions`, or an `inputType` set on one side only | Compare model options across deployments |
| `provider.retries` rising, throughput falling | Rate limiting | Enable `collectRateLimitHeaders` and add a `rateLimiter` |
| `search.results` below `k` on filtered searches | On vector sets, the filter is more selective than the search effort | Raise `filterEffort`, or use `exact(true)` |
| `cache.nearMiss` high while hits are low | Entries exist but the threshold refuses them | Inspect similarities with `lookupAll` before changing the threshold |
| `memory.pending` non-zero and not falling | Turns are leaving the window without reaching the archive | Check the archive store; call `drain` |
