## Tomcat Session

Redisson implements a Session Manager for [Apache Tomcat](http://tomcat.apache.org) that utilizes Valkey or Redis as the storage backend. It stores web session in Valkey or Redis, facilitating the distribution of requests across multiple Tomcat servers in a cluster environment. 

The implementation supports non-sticky session management, which means users can be routed to any server in the application cluster without losing their session data, as the session information is stored in Valkey or Redis rather than on individual Tomcat instances.

Supports Apache Tomcat versions 7.x up to 11.x

Usage:

**1. Add session manager**

Add `RedissonSessionManager` in global context - `tomcat/conf/context.xml` or per application context - `tomcat/conf/server.xml`

```xml
<Manager className="org.redisson.tomcat.RedissonSessionManager"
	 configPath="${catalina.base}/redisson.conf" 
	 readMode="REDIS" updateMode="DEFAULT" broadcastSessionEvents="false"
	 keyPrefix=""/>
```

`keyPrefix` - string prefix applied to all keys. Allows to connect different Tomcat environments to the same Redis or Valkey instance.

`readMode` - read Session attributes mode. Two modes are available:  

* `MEMORY` - stores attributes into local Tomcat Session and Redis. Further Session updates propagated to local Tomcat Session using Redis-based events.
* `REDIS` - stores attributes into Redis or Valkey only.  Default mode.

`broadcastSessionEvents` - if `true` then `sessionCreated` and `sessionDestroyed` events are broadcasted across all Tomcat instances and cause all registered HttpSessionListeners to be triggered. Default is `false`.

`broadcastSessionUpdates` - if `true` and `readMode=MEMORY` then session updates are broadcasted across all Tomcat instances. Default is `true`.

`updateMode` - Session attributes update mode. Two modes are available:  

   * `DEFAULT` - session attributes are stored into Redis or Valkey only through the `Session.setAttribute` method. Default mode.
   * `AFTER_REQUEST`
       * In `readMode=REDIS` all changes of session attributes made through the `Session.setAttribute` method are accumulated in memory and stored into Redis or Valkey only after the end of the request. 
       * In `readMode=MEMORY` all session attributes are always stored into Redis or Valkey after the end of the request regardless of the `Session.setAttribute` method invocation. It is useful in case when some objects stored in session change their own state without `Session.setAttribute` method execution. Updated attributes are removed from all other Session instances if `broadcastSessionUpdates=true` and reloaded from Redis or Valkey when these attributes are requested.  

`configPath` - path to Redisson YAML config. See [configuration](configuration.md) page for more details. In case session manager is configured programatically, a config object can be passed using the `setConfig()` method

Shared Redisson instance  

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

**2. Copy two jars into `TOMCAT_BASE/lib` directory:**


[redisson-all-3.45.1.jar](https://repo1.maven.org/maven2/org/redisson/redisson-all/3.45.1/redisson-all-3.45.1.jar)

Tomcat 7.x - [redisson-tomcat-7-3.45.1.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-7/3.45.1/redisson-tomcat-7-3.45.1.jar)  

Tomcat 8.x - [redisson-tomcat-8-3.45.1.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-8/3.45.1/redisson-tomcat-8-3.45.1.jar)  

Tomcat 9.x - [redisson-tomcat-9-3.45.1.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-9/3.45.1/redisson-tomcat-9-3.45.1.jar)  

Tomcat 10.x - [redisson-tomcat-10-3.45.1.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-10/3.45.1/redisson-tomcat-10-3.45.1.jar)  

Tomcat 11.x - [redisson-tomcat-11-3.45.1.jar](https://repo1.maven.org/maven2/org/redisson/redisson-tomcat-11/3.45.1/redisson-tomcat-11-3.45.1.jar)  

[Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)

## Spring Session

For information on using Spring Session implementation, please refer to the [Spring Session](integration-with-spring.md/#spring-session) documentation.

## Micronaut Session

For information on using Micronaut Session implementation, please refer to the[Micronaut Session](microservices-integration.md/#session) documentation.