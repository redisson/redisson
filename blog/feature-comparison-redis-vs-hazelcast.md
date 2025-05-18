# Feature Comparison: Redis vs Hazelcast

#### Introduction

Redis and Hazelcast are two popular options for building an in-memory database - but which one is best for your purposes? In this article, we'll discuss how you can make the right choice between Redis and Hazelcast.

#### What is Redis?

Redis is an open-source in-memory key-value data structure store that can be used to implement a database, cache, and message broker. The Redis software is written in C and includes support for high-level data types such as strings, lists, maps, and sets. Many developers like Redis for its lightweight footprint, good performance, and high availability.

#### What is Hazelcast?

Hazelcast is an open-source in-memory data grid platform written in the Java programming language that is often used to implement caches. The basic units of Hazelcast are nodes and clusters. Each of the nodes in a cluster is tasked with managing part of the data. This distributed system makes I/O and processing much more efficient.

  

#### 3 Major Differences Between Redis and Hazelcast

#### Threading

Although [Redis](https://redis.io) is single-threaded, it uses a high-performance core with a very low memory footprint. This advantage enables you to easily run multiple Redis instances on a single machine, making full use of all CPU cores.

The "split brain" problem is a networking issue in which nodes lose communications between them. Each node believes that it is the primary node, which can result in data corruption as multiple nodes access the same file or disk. Redis' single-threaded model is able to protect against the split brain problem during write operations; however, Hazelcast's multi-threaded model cannot.

#### Clustering

Hazelcast provides automatic discovery of multicast routers through the UDP protocol. Redis, on the other hand, does not. The developers of Redis believe that automatic discovery does not save time when compared with the costs of troubleshooting and administering the full environment.

For this reason, Redis is available as a fully managed service from many cloud providers, including:

*   [Amazon ElastiCache](https://aws.amazon.com/elasticache/)
*   [Amazon MemoryDB](https://aws.amazon.com/memorydb)
*   [Azure Cache for Redis](https://azure.microsoft.com/en-us/services/cache/)
*   [Google Cloud Memorystore for Redis](https://cloud.google.com/memorystore/docs/redis/)
*   [Google Cloud Memorystore for Valkey](https://cloud.google.com/memorystore/docs/redis/)
*   [Oracle OCI Cache](https://docs.oracle.com/en-us/iaas/Content/ocicache/managingclusters.htm)
*   [IBM Cloud Databases for Redis](https://cloud.ibm.com/docs/databases-for-redis)
*   [Redis on SAP BTP](https://www.sap.com/products/technology-platform/redis-on-sap-btp-hyperscaler-option.html)

These fully managed services offer advantages such as full Redis automation, support, monitoring, and administration. As such, they allow developers to focus on building their applications, not on the database itself.

#### Memory handling

Redis easily handles terabytes of RAM, thanks to the reliable [jemalloc](http://jemalloc.net/) memory allocator. Data types such as hashes, lists, and sets are encoded to use memory very efficiently, with an average savings of 5 times less memory.

Meanwhile, the non-commercial version of Hazelcast stores all distributed data in on-heap memory served by the Java garbage collector. As the amount of data grows, therefore, garbage collection might cause pauses in the execution of the application. These pauses affect the application's performance and may also cause more serious problems and errors.

#### Comparison

Redis does not include compatibility with the Java programming language right out of the box. Instead, many Redis users make use of Java clients like [Redisson](https://redisson.pro) in order to gain access to Java objects, collections, and services based on Redis.

In this section, we'll discuss the differences between Redisson and Hazelcast in terms of their features. In general, Redisson includes more Java functionality than Hazelcast.

##### Distributed collections

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| Map | Yes | Yes |
| JSON Store | Yes | No  |
| Multimap | Yes | Yes |
| Set | Yes | Yes |
| List | Yes | Yes |
| Queue | Yes | Yes |
| Deque | Yes | No  |
| SortedSet | Yes | No  |
| ScoredSortedSet | Yes | No  |
| PriorityQueue | Yes | No  |
| PriorityDeque | Yes | No  |
| DelayedQueue | Yes | No  |
| TransferQueue | Yes | No  |
| RingBuffer | Yes | Yes |
| TimeSeries | Yes | No  |

##### Distributed locks and synchronizers

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| Lock | Yes | Yes |
| Semaphore | Yes | Yes |
| CountDownLatch | Yes | Yes |
| FairLock | Yes | No  |
| Fenced Lock | Yes | No  |
| Spin Lock | Yes | No  |
| MultiLock | Yes | No  |
| ReadWriteLock | Yes | No  |
| PermitExpirableSemaphore | Yes | No  |

##### Distributed objects

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| Object holder | Yes | Yes |
| JSON holder | Yes | No  |
| AtomicLong | Yes | Yes |
| LongAdder | Yes | No  |
| DoubleAdder | Yes | No  |
| Publish/Subscribe | Yes | Yes |
| Reliable Publish/Subscribe | Yes | Yes |
| Id Generator | Yes | Yes |
| AtomicDouble | Yes | No  |
| Geospatial | Yes | No  |
| BitSet | Yes | No  |
| BloomFilter | Yes | No  |
| BinaryStream | Yes | No  |
| HyperLogLog | Yes | Yes |
| RateLimiter | Yes | No  |

##### Advanced cache support

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| JCache API | Yes | Yes |
| JCache API with near cache (up to 45x faster) | Yes | Yes |
| Near Cache | Yes | Yes |

##### API architecture

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| Asynchronous API | Yes | partial support |
| Reactive API | Yes | No  |
| RxJava3 API | Yes | No  |

##### Transactions

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| Transactions API | Yes | Yes |
| XA Transactions | Yes | Yes |

##### Distributed services

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| ExecutorService | Yes | Yes |
| MapReduce | Yes | Yes |
| SchedulerService | Yes | Yes |
| RemoteService | Yes | No  |
| LiveObjectService | Yes | No  |
| RediSearch | Yes | No  |

##### Integration with frameworks

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| Spring Cache | Yes | Yes |
| Spring Cache with near cache (up to 45x faster) | Yes | No  |
| Hibernate Cache | Yes | Yes |
| Hibernate Cache with near cache (up to 5x faster) | Yes | No  |
| MyBatis Cache | Yes | Yes |
| MyBatis Cache with near cache (up to 45x faster) | Yes | No  |
| Quarkus Cache | Yes | No  |
| Quarkus Cache with near cache  <br>(up to 45x faster) | Yes | No  |
| Micronaut Cache | Yes | Yes |
| Micronaut Cache with near cache  <br>(up to 45x faster) | Yes | No  |
| Micronaut Session | Yes | No  |
| Tomcat Session Manager | Yes | Yes |
| Spring Session | Yes | Yes |
| Statistics monitoring | 20 different statistics monitoring systems including JMX | JMX, NewRelic, AppDynamics |

##### Security

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| Authentication | Yes | Yes |
| Authorization | Yes | Yes |
| SSL support | Yes | Yes |

##### Custom data serialization

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| JSON codec | Yes | No  |
| JDK Serialization | Yes | No  |
| Avro codec | Yes | No  |
| Smile codec | Yes | No  |
| CBOR codec | Yes | No  |
| MsgPack codec | Yes | No  |
| Kryo codec | Yes | No  |
| FST codec | Yes | No  |
| LZ4 compression codec | Yes | No  |
| Snappy compression codec | Yes | No  |

##### Stability and ease of use

|     | Redis + Redisson | Hazelcast |
| --- | --- | --- |
| Fully-managed services support  <br>(AWS Elasticache, Azure Cache, Redis Enterprise, Google Cloud...) | Yes | No  |
| Large memory amount handling | Yes | Open-source version has limitations |
