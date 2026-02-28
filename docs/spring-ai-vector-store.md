## Spring AI Vector Store

*This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition.*

Redisson provides [Spring AI](https://spring.io/projects/spring-ai) Vector Store implementation for building AI-powered applications. It supports a wide range of use cases including Retrieval Augmented Generation (RAG), semantic search, document similarity and recommendations, and memory for AI agents. The implementation leverages Redis Stack with RediSearch and RedisJSON modules to store and query vector embeddings.

The store uses Redis JSON documents to persist vector embeddings along with their associated document content and metadata. It leverages RediSearch for creating and querying vector similarity indexes.

**Features:**

- Vector similarity search using KNN (K-Nearest Neighbors)
- Support for HNSW and FLAT vector indexing algorithms
- Multiple distance metrics: COSINE, L2 (Euclidean), Inner Product
- Configurable metadata fields (TEXT, TAG, NUMERIC) for advanced filtering
- Automatic schema initialization
- Portable filter expressions
- Batch processing support
- Semantic search over natural language corpora
- Document similarity and content-based recommendations
- Persistent memory for AI agents and multi-turn conversations

### Prerequisites

1. **Redis Stack** or **Redis 8.4+** instance with RediSearch and RedisJSON modules

2. **EmbeddingModel** instance to compute the document embeddings. Several options are available:
  
     - [OpenAI](https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html)
     - [Ollama](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html)
     - [Other supported providers](https://docs.spring.io/spring-ai/reference/api/embeddings.html#available-implementations)

### Usage

**1. Add dependency into your project**

**Spring Boot Starter** (recommended)

For Spring Boot applications with auto-configuration support:

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>redisson-spring-ai-store-starter-10</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
compile 'pro.redisson:redisson-spring-ai-store-starter-10:xVERSIONx'
```

**Store Implementation Only**

For manual configuration or non-Spring Boot applications:

Maven
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>redisson-spring-ai-store-10</artifactId>
    <version>xVERSIONx</version>
</dependency>
```

Gradle
```groovy
compile 'pro.redisson:redisson-spring-ai-store-10:xVERSIONx'
```

[License key configuration](../configuration/#license-key-configuration)

**2. Add settings into `application.yaml` file**

```yaml
spring:
  ai:
    vectorstore:
      redisson:
        index-name: my-index
        prefix: doc:
        initialize-schema: true
        vector-algorithm: HNSW
        distance-metric: COSINE
        hnsw:
          m: 16
          ef-construction: 200
          ef-runtime: 10
        metadata-fields:
          - name: category
            type: TAG
          - name: year
            type: NUMERIC
```

**3. Use VectorStore in your application**

```java
@Autowired 
VectorStore vectorStore;

// ...

List<Document> documents = List.of(
    new Document("Spring AI rocks!! Spring AI rocks!!", Map.of("category", "framework", "year", 2024)),
    new Document("The World is Big and Salvation Lurks Around the Corner"),
    new Document("You walk forward facing the past and you turn back toward the future.", Map.of("category", "philosophy", "year", 2023)));

// Add the documents to Redis
vectorStore.add(documents);

// Retrieve documents similar to a query
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("Spring")
        .topK(5)
        .build());
```

### Configuration

Properties starting with `spring.ai.vectorstore.redisson.*` are used to configure the Vector Store:

| Property | Description | Default Value |
|----------|-------------|---------------|
| `spring.ai.vectorstore.redisson.index-name` | Name of the Redis search index | `spring-ai-index` |
| `spring.ai.vectorstore.redisson.prefix` | Prefix for Redis keys | `embedding:` |
| `spring.ai.vectorstore.redisson.initialize-schema` | Whether to initialize the required schema | `false` |
| `spring.ai.vectorstore.redisson.vector-algorithm` | Vector indexing algorithm (`HNSW` or `FLAT`) | `HNSW` |
| `spring.ai.vectorstore.redisson.distance-metric` | Distance metric (`COSINE`, `L2`, or `IP`) | `COSINE` |

The `initialize-schema` property must be set to `true` for automatic index creation. This is a breaking change from earlier Spring AI versions where schema initialization happened by default.

**Vector Algorithms**

- `HNSW`

	Hierarchical Navigable Small World - default algorithm that provides better search performance with slightly higher memory usage. Recommended for most use cases.

	- `m` - Controls the number of bi-directional links created for each node. Higher values improve recall but increase memory usage. Recommended range: 12-48.
	- `ef-construction` - Determines search width during index building. Higher values create higher quality indexes at the cost of longer construction time. Should be at least `2 * m`. Recommended range: 100-500.
	- `ef-runtime` - Controls search precision at query time. Higher values improve recall but increase query latency. Recommended range: 10-100.

- `FLAT`

	Brute force algorithm that provides exact results but slower performance for large datasets. Use when exact accuracy is required and dataset size is small.

HNSW Algorithm Parameters:

| Property | Description | Default Value |
|----------|-------------|---------------|
| `spring.ai.vectorstore.redisson.hnsw.m` | Maximum number of connections per node | `16` |
| `spring.ai.vectorstore.redisson.hnsw.ef-construction` | Search width during index building | `200` |
| `spring.ai.vectorstore.redisson.hnsw.ef-runtime` | Search width during query execution | `10` |

**Metadata fields**

Metadata fields enable filtering capabilities during similarity searches. You must explicitly define all metadata field names and types for any metadata field used in filter expressions.

Metadata Field Types:

| Type | Description | Use Case |
|------|-------------|----------|
| `TAG` | Exact match filtering | Categorical data, labels, status values |
| `TEXT` | Full-text search | Descriptions, content fields |
| `NUMERIC` | Range queries | Years, prices, counts, scores |

Configuration example:

```yaml
spring:
  ai:
    vectorstore:
      redisson:
        index-name: "my-index"
        prefix: "doc:"
        initialize-schema: true
        vector-algorithm: HNSW
        distance-metric: COSINE
        hnsw:
          m: 16
          ef-construction: 200
          ef-runtime: 10	  
        metadata-fields:
          - name: category
            type: TAG
          - name: description
            type: TEXT
          - name: year
            type: NUMERIC
          - name: price
            type: NUMERIC
```

**Metadata Filtering**

You can use the generic [metadata filters](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#metadata-filters) with Redisson Vector Store.

Using text expression language:

```java
vectorStore.similaritySearch(SearchRequest.builder()
        .query("The World")
        .topK(5)
        .similarityThreshold(0.7)
        .filterExpression("country in ['UK', 'NL'] && year >= 2020")
        .build());
```

Using programmatic Filter.Expression DSL:

```java
FilterExpressionBuilder b = new FilterExpressionBuilder();

vectorStore.similaritySearch(SearchRequest.builder()
        .query("The World")
        .topK(5)
        .similarityThreshold(0.7)
        .filterExpression(b.and(
                b.in("country", "UK", "NL"),
                b.gte("year", 2020)).build())
        .build());
```

Filter expressions are automatically converted into [Redis search queries](https://redis.io/docs/interact/search-and-query/query/). For example, the portable filter expression `country in ['UK', 'NL'] && year >= 2020` is converted into the Redis filter format `@country:{UK | NL} @year:[2020 inf]`.

**Distance Metrics**

The Vector Store supports three distance metrics:

| Metric | Description | Best For |
|--------|-------------|----------|
| `COSINE` | Cosine similarity (default) | Text embeddings, semantic similarity |
| `L2` | Euclidean distance | Image embeddings, spatial data |
| `IP` | Inner Product | Pre-normalized embeddings |

Each metric is automatically normalized to a 0-1 similarity score, where 1 indicates maximum similarity.

*Manual Configuration*

Instead of using the Spring Boot auto-configuration, you can manually configure the Redisson Vector Store:

```java
@Configuration
public class VectorStoreConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redisson() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://127.0.0.1:6379");
        return Redisson.create(config);
    }

    @Bean
    public VectorStore vectorStore(RedissonClient redissonClient, EmbeddingModel embeddingModel) {
        return RedissonVectorStore.builder(redissonClient, embeddingModel)
            .indexName("custom-index")
            .prefix("custom-prefix")
            .vectorAlgorithm(Algorithm.HNSW)
            .distanceMetric(DistanceMetric.COSINE)
            .hnswM(16)
            .hnswEfConstruction(200)
            .hnswEfRuntime(10)
            .metadataFields(
                MetadataField.tag("category"),
                MetadataField.numeric("year"),
                MetadataField.text("description"))
            .initializeSchema(true)
            .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        // Configure your embedding model (OpenAI, Ollama, etc.)
        return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
    }
}
```

### RAG Integration Example

Combine the vector store with Spring AI's `ChatClient` for Retrieval Augmented Generation:

```java
@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public String askQuestion(String question) {
        // Retrieve relevant documents
        List<Document> relevantDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.7)
                .build()
        );

        // Build context from retrieved documents
        String context = relevantDocs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));

        // Generate response with context
        return chatClient.prompt()
            .user(u -> u.text("""
                Based on the following context, answer the question.
                
                Context:
                {context}
                
                Question: {question}
                """)
                .param("context", context)
                .param("question", question))
            .call()
            .content();
    }
}
```
### Semantic Search Example

Unlike keyword search, semantic search finds results based on *meaning* rather than exact word matches. This is useful for documentation search, support knowledge bases, product catalogs, or any corpus where users may phrase queries in unexpected ways.

Configure metadata fields for filtering in `application.yaml`:

```yaml
spring:
  ai:
    vectorstore:
      redisson:
        index-name: "semantic-search-index"
        prefix: "doc:"
        initialize-schema: true
        metadata-fields:
          - name: source
            type: TAG
          - name: category
            type: TAG
```

Index documents and run semantic queries:

```java
@Service
public class SemanticSearchService {

    private final VectorStore vectorStore;

    public SemanticSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Index documents
    public void indexDocuments(List<String> texts, String source) {
        List<Document> documents = texts.stream()
            .map(text -> new Document(text, Map.of("source", source)))
            .toList();

        vectorStore.add(documents);
    }

    // Search by natural language query
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.7)
                .build());
    }

    // Search scoped to a specific source with metadata filtering
    public List<Document> searchBySource(String query, String source) {
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.7)
                .filterExpression("source == '" + source + "'")
                .build());
    }
}
```

Usage example:

```java
// Index documents from a known source
searchService.indexDocuments(List.of(
    "Spring Boot simplifies Java application development",
    "Docker containers enable consistent deployments",
    "Kubernetes orchestrates containerized workloads",
    "PostgreSQL is a powerful open-source relational database"
), "knowledge-base");

// Search across all indexed documents — returns results about containers and orchestration
List<Document> results = searchService.search("how do I deploy my app reliably", 5);

// Search scoped to a specific source only
List<Document> scoped = searchService.searchBySource("relational database options", "knowledge-base");
```

### Document Similarity & Recommendations Example

Vector embeddings can power content-based recommendation engines — surfacing articles, products, or documents that are semantically close to a given item or to a user's recent activity.

Configure metadata fields in `application.yaml`:

```yaml
spring:
  ai:
    vectorstore:
      redisson:
        index-name: "recommendations-index"
        prefix: "article:"
        initialize-schema: true
        metadata-fields:
          - name: articleId
            type: TAG
          - name: title
            type: TEXT
          - name: category
            type: TAG
```

Store articles and retrieve recommendations:

```java
@Service
public class RecommendationService {

    private final VectorStore vectorStore;

    public RecommendationService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Store an article with metadata
    public void addArticle(String id, String title, String content, String category) {
        vectorStore.add(List.of(new Document(
            content,
            Map.of("articleId", id, "title", title, "category", category)
        )));
    }

    // Find articles similar to a given article's content
    public List<Map<String, Object>> findSimilarArticles(String articleContent, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(articleContent)
                    .topK(topK + 1) // +1 to account for the article itself
                    .similarityThreshold(0.75)
                    .build())
            .stream()
            .map(doc -> Map.of(
                "id",       doc.getMetadata().get("articleId"),
                "title",    doc.getMetadata().get("title"),
                "category", doc.getMetadata().get("category"),
                "score",    doc.getScore()
            ))
            .toList();
    }

    // Recommend articles based on a user's reading history
    public List<Map<String, Object>> recommendFromHistory(List<String> readArticleContents) {
        // Combine recent reading history into a single semantic query
        String combinedContext = String.join(" ", readArticleContents);

        return vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(combinedContext)
                    .topK(10)
                    .similarityThreshold(0.65)
                    .build())
            .stream()
            .map(doc -> Map.of(
                "id",       doc.getMetadata().get("articleId"),
                "title",    doc.getMetadata().get("title"),
                "category", doc.getMetadata().get("category"),
                "score",    doc.getScore()
            ))
            .toList();
    }
}
```

Usage example:

```java
// Index some articles
recommendationService.addArticle("1", "Intro to Spring AI",      "Spring AI intro content...",    "AI");
recommendationService.addArticle("2", "LangChain vs Spring AI",  "Comparison article content...", "AI");
recommendationService.addArticle("3", "Docker Best Practices",   "Container content...",          "DevOps");

// Get articles similar to article 1 — will surface article 2 as highly similar
List<Map<String, Object>> similar = recommendationService.findSimilarArticles("Spring AI intro content...", 3);

// Recommend based on a user who recently read articles 1 and 2
List<Map<String, Object>> recommended = recommendationService.recommendFromHistory(
    List.of("Spring AI intro content...", "Comparison article content..."));
```

### Memory for AI Agents Example

Agents and multi-turn chat applications can use the vector store as a long-term memory layer, persisting conversation turns and facts as embeddings and retrieving the most contextually relevant ones at each step.

Configure metadata fields in `application.yaml`:

```yaml
spring:
  ai:
    vectorstore:
      redisson:
        index-name: "agent-memory-index"
        prefix: "memory:"
        initialize-schema: true
        metadata-fields:
          - name: sessionId
            type: TAG
          - name: type
            type: TAG
          - name: timestamp
            type: TEXT
```

Store and recall memories, then use them to augment chat responses:

```java
@Service
public class AgentMemoryService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public AgentMemoryService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    // Persist a memory (conversation turn, fact, or observation)
    public void remember(String sessionId, String memoryText, String type) {
        vectorStore.add(List.of(new Document(
            memoryText,
            Map.of(
                "sessionId", sessionId,
                "type",      type,          // "conversation", "fact", "preference"
                "timestamp", Instant.now().toString()
            )
        )));
    }

    // Recall memories relevant to the current input, scoped to a session
    public List<Document> recall(String sessionId, String currentInput, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(currentInput)
                .topK(topK)
                .similarityThreshold(0.6)
                .filterExpression("sessionId == '" + sessionId + "'")
                .build());
    }

    // Chat with memory-augmented context
    public String chat(String sessionId, String userMessage) {
        // 1. Recall relevant past memories for this session
        List<Document> memories = recall(sessionId, userMessage, 5);

        String memoryContext = memories.isEmpty()
            ? "No relevant memories."
            : memories.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n- ", "- ", ""));

        // 2. Build a system prompt that injects the recalled context
        String systemPrompt = """
            You are a helpful assistant with memory of past interactions.

            Relevant context from memory:
            %s

            Use this context to personalize your response where appropriate.
            """.formatted(memoryContext);

        // 3. Call the LLM with the enriched prompt
        String response = chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .call()
            .content();

        // 4. Persist this interaction for future recall
        remember(sessionId, "User asked: " + userMessage, "conversation");
        remember(sessionId, "Assistant responded: " + response, "conversation");

        return response;
    }
}
```

Usage example:

```java
// Teach the agent facts about a user
memoryService.remember("user-42", "User prefers concise answers", "fact");
memoryService.remember("user-42", "User is a Java developer", "fact");
memoryService.remember("user-42", "User dislikes verbose responses", "preference");

// Start a conversation — relevant facts and past turns are recalled automatically
String reply1 = memoryService.chat("user-42", "What is Spring AI?");

// Follow-up turn — the agent remembers the previous exchange and user preferences
String reply2 = memoryService.chat("user-42", "How do I add a vector store to it?");

// Recall only facts stored for a user, filtered by type
List<Document> facts = memoryService.recall("user-42", "communication style", 5)
    .stream()
    .filter(doc -> "fact".equals(doc.getMetadata().get("type")))
    .toList();
```
