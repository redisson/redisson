# Redis based Tomcat Session Manager

Stores session of [Apache Tomcat](http://tomcat.apache.org) in Redis and allows to distribute requests across a cluster of Tomcat servers. Implements non-sticky session management backed by Redis.

Supports Apache Tomcat 7.x, 8.x, 9.x, 10.x

## Usage

### 1. Add session manager

Add `RedissonSessionManager` in global context - `tomcat/conf/context.xml` or per application context - `tomcat/conf/server.xml`

   ```xml
<Manager className="org.redisson.tomcat.RedissonSessionManager"
	 configPath="${catalina.base}/redisson.conf" 
	 readMode="REDIS" updateMode="DEFAULT" broadcastSessionEvents="false"
	 keyPrefix=""/>
   ```
   `keyPrefix` - string prefix applied to all Redis keys. Allows to connection different Tomcat envirounments to the same Redis instance.
   
   `readMode` - read Session attributes mode. Two modes are available:
   * `MEMORY` - stores attributes into local Tomcat Session and Redis. Further Session updates propagated to local Tomcat Session using Redis-based events.
   * `REDIS` - stores attributes into Redis only.  Default mode.
   <br/>

   `broadcastSessionEvents` - if `true` then `sessionCreated` and `sessionDestroyed` events are broadcasted across all Tomcat instances and cause all registered HttpSessionListeners to be triggered. Default is `false`.
   
   `broadcastSessionUpdates` - if `true` and `readMode=MEMORY` then session updates are broadcasted across all Tomcat instances. Default is `true`.

   `updateMode` - Session attributes update mode. Two modes are available:
   * `DEFAULT` - session attributes are stored into Redis only through `Session.setAttribute` method. Default mode.
   * `AFTER_REQUEST` - all session attributes are stored into Redis after each request. It useful in case when some objects stored in session change own state without `Session.setAttribute` method execution.
   <br/>

   `configPath` - path to Redisson YAML config. See [configuration wiki page](https://github.com/redisson/redisson/wiki/2.-Configuration) for more details.

#### Shared Redisson instance

Amount of Redisson instances created by Tomcat for multiple contexts could be reduced through JNDI registry:

1. Add shared redisson instance produced by `JndiRedissonFactory` into `tomcat/conf/server.xml` in `GlobalNamingResources` tag area:

```xml
  <GlobalNamingResources>
    <Resource name="bean/redisson"
	      auth="Container"
              factory="org.redisson.JndiRedissonFactory"
              configPath="${catalina.base}/conf/redisson.yaml"
	      closeMethod="shutdown"/>
  </GlobalNamingResources>
```

2. Add `JndiRedissonSessionManager` with resource link to redisson instance into `tomcat/conf/context.xml`

```xml
    <ResourceLink name="bean/redisson"
                  global="bean/redisson"
		  type="org.redisson.api.RedissonClient" />

    <Manager className="org.redisson.tomcat.JndiRedissonSessionManager"
         readMode="REDIS"
         jndiName="bean/redisson" />
```

### 2. Copy two jars into `TOMCAT_BASE/lib` directory:

  
[redisson-all-3.15.6.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=3.15.6&e=jar)
  
Tomcat 7.x - [redisson-tomcat-7-3.15.6.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-7&v=3.15.6&e=jar)  

Tomcat 8.x - [redisson-tomcat-8-3.15.6.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-8&v=3.15.6&e=jar)  

Tomcat 9.x - [redisson-tomcat-9-3.15.6.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-9&v=3.15.6&e=jar)  

Tomcat 10.x - [redisson-tomcat-10-3.15.6.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-10&v=3.15.6&e=jar)  

Consider __[Redisson PRO](https://redisson.pro)__ version for **ultra-fast performance** and **support by SLA**.
