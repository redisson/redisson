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
   `keyPrefix` - string prefix applied to all Redis keys. Allows to connect different Tomcat environments to the same Redis instance.
   
   `readMode` - read Session attributes mode. Two modes are available:
   * `MEMORY` - stores attributes into local Tomcat Session and Redis. Further Session updates propagated to local Tomcat Session using Redis-based events.
   * `REDIS` - stores attributes into Redis only.  Default mode.
   <br/>

   `broadcastSessionEvents` - if `true` then `sessionCreated` and `sessionDestroyed` events are broadcasted across all Tomcat instances and cause all registered HttpSessionListeners to be triggered. Default is `false`.
   
   `broadcastSessionUpdates` - if `true` and `readMode=MEMORY` then session updates are broadcasted across all Tomcat instances. Default is `true`.

   `updateMode` - Session attributes update mode. Two modes are available:
   * `DEFAULT` - session attributes are stored into Redis only through the `Session.setAttribute` method. Default mode.
   * `AFTER_REQUEST`
       * In `readMode=REDIS` all changes of session attributes made through the `Session.setAttribute` method are accumulated in memory and stored into Redis only after the end of the request. 
       * In `readMode=MEMORY` all session attributes are always stored into Redis after the end of the request regardless of the `Session.setAttribute` method invocation. It is useful in case when some objects stored in session change their own state without `Session.setAttribute` method execution. Updated attributes are removed from all other Session instances if `broadcastSessionUpdates=true` and reloaded from Redis when these attributes are requested.  
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

  
[redisson-all-3.30.0.jar](https://repo1.maven.org/maven2/org/redisson/redisson-all/3.30.0/redisson-all-3.30.0.jar)
  
Tomcat 7.x - [redisson-tomcat-7-3.30.0.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-7/3.30.0/redisson-tomcat-7-3.30.0.jar)  

Tomcat 8.x - [redisson-tomcat-8-3.30.0.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-8/3.30.0/redisson-tomcat-8-3.30.0.jar)  

Tomcat 9.x - [redisson-tomcat-9-3.30.0.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-9/3.30.0/redisson-tomcat-9-3.30.0.jar)  

Tomcat 10.x - [redisson-tomcat-10-3.30.0.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-10/3.30.0/redisson-tomcat-10-3.30.0.jar)  

Try __[Redisson PRO](https://redisson.pro)__ with **ultra-fast performance** and **support by SLA**.
