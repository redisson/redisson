Redis based Hibernate Cache implementation
===

Implements Hibernate 2nd level Cache provider based on Redisson.

Supports Hibernate 4.x, 5.x

<sub>Please consider __[Redisson PRO](https://redisson.pro)__ version for advanced features and support by SLA.</sub>

Usage
===

### 1.  Add `redisson-hibernate` dependency into your project:

1. __For JDK 1.8+__  

     Maven
     ```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <!-- for Hibernate v4.x -->
         <artifactId>redisson-hibernate-4</artifactId>
         <!-- for Hibernate v5.0.x - v5.1.x -->
         <artifactId>redisson-hibernate-5</artifactId>
         <!-- for Hibernate v5.2.x -->
         <artifactId>redisson-hibernate-52</artifactId>
         <!-- for Hibernate v5.3.x -->
         <artifactId>redisson-hibernate-53</artifactId>
         <version>3.10.0</version>
     </dependency>
     ```
     Gradle

     ```java
     // for Hibernate v4.x
     compile 'org.redisson:redisson-hibernate-4:3.10.0'
     // for Hibernate v5.0.x - v5.1.x
     compile 'org.redisson:redisson-hibernate-5:3.10.0'
     // for Hibernate v5.2.x
     compile 'org.redisson:redisson-hibernate-52:3.10.0'
     // for Hibernate v5.3.x
     compile 'org.redisson:redisson-hibernate-53:3.10.0'
     ```  

2. __For JDK 1.6+__  

     Maven
     ```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <!-- for Hibernate v4.x -->
         <artifactId>redisson-hibernate-4</artifactId>
         <!-- for Hibernate v5.0.x - v5.1.x -->
         <artifactId>redisson-hibernate-5</artifactId>
         <version>2.15.0</version>
     </dependency>
     ```
     Gradle

     ```java
     // for Hibernate v4.x
     compile 'org.redisson:redisson-hibernate-4:2.15.0'
     // for Hibernate v5.0.x - v5.1.x
     compile 'org.redisson:redisson-hibernate-5:2.15.0'
     ``` 


### 2. Specify hibernate cache settings

```xml
<!-- 2nd level cache activation -->
<property name="hibernate.cache.use_second_level_cache" value="true" />
<property name="hibernate.cache.use_query_cache" value="true" />

<!-- Redisson Hibernate Cache factory -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonRegionFactory" />
<!-- Redisson YAML config (searched in filesystem and classpath) -->
<property name="hibernate.cache.redisson.config" value="/redisson.yaml" />
<!-- Redisson JSON config (searched in filesystem and classpath) -->
<property name="hibernate.cache.redisson.config" value="/redisson.json" />
```

Redisson allows to define follow cache settings per entity, collection, naturalid, query and timestamp regions:  

`eviction.max_entries` - max size of cache. Superfluous entries are evicted using LRU algorithm. `0` value means unbounded cache. Default value: 0  

`expiration.time_to_live` - time to live per cache entry in milliseconds. `0` value means this setting doesn't affect expiration. Default value: 0  

`expiration.max_idle_time` - max idle time per cache entry in milliseconds. `0` value means this setting doesn't affect expiration. Default value: 0  


Configuration examples:

```xml
<!-- cache definition for entity region. Example region name: "my_object" -->
<property name="hibernate.cache.redisson.my_object.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.my_object.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.my_object.expiration.max_idle_time" value="300000" />

<!-- cache definition for collection region. Example region name: "my_list" -->
<property name="hibernate.cache.redisson.my_list.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.my_list.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.my_list.expiration.max_idle_time" value="300000" />

<!-- cache definition for naturalid region. Suffixed by ##NaturalId. Example region name: "my_object" -->
<property name="hibernate.cache.redisson.my_object##NaturalId.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.my_object##NaturalId.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.my_object##NaturalId.expiration.max_idle_time" value="300000" />

<!-- cache definition for query region. Example region name: "my_query" -->
<property name="hibernate.cache.redisson.my_entity.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.my_entity.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.my_entity.expiration.max_idle_time" value="300000" />

<!-- cache definition for timestamps region. -->
<property name="hibernate.cache.redisson.timestamps.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.timestamps.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.timestamps.expiration.max_idle_time" value="300000" />

```
