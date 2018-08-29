Redis based Tomcat Session Manager
===

Stores session of Apache Tomcat in Redis and allows to distribute requests across a cluster of Tomcat servers. Implements non-sticky session management backed by Redis.  

Supports Apache Tomcat 6.x, 7.x, 8.x, 9.x

Advantages
===

Current implementation differs from any other Redis based Tomcat Session Manager in terms of efficient storage and optimized writes. Each session attribute is written into Redis during each `HttpSession.setAttribute` invocation. While other solutions serialize whole session each time.

Usage
===

**1** Add `RedissonSessionManager` into `tomcat/conf/context.xml`
   
   ```xml
<Manager className="org.redisson.tomcat.RedissonSessionManager"
	         configPath="${catalina.base}/redisson.conf" readMode="MEMORY" updateMode="DEFAULT"/>
   ```
   `readMode` - read attributes mode. Two modes are available:
   * `MEMORY` - stores attributes into local Tomcat Session and Redis. Further Session updates propagated to local Tomcat Session using Redis-based events. Default mode.
   * `REDIS` - stores attributes into Redis only.  
   <br/>

   `updateMode` - attributes update mode. Two modes are available:
   * `DEFAULT` - session attributes are stored into Redis only through setAttribute method. Default mode.
   * `AFTER_REQUEST` - all session attributes are stored into Redis after each request.
   <br/>

   `sharedSession` - share session across multiple deployed applications. Appropriate solution for migration of EAR based application with multiple WARs hosted previously on JBoss, WebLogic...  Works only in `readMode=REDIS`.  
   <i>This option available only in [Redisson PRO](http://redisson.pro) edition.</i>  
   
   * `false` - don't share single session. Default mode.  
   * `true` - share single session.
   
   Requires to set `crossContext` setting in `tomcat/conf/context.xml`
   ```xml
   <Context crossContext="true">
   ...   
   </Context>
   ```	    
   Cookie path should be the same for all applications and defined in `web.xml`
   ```xml
   <session-config>
      <cookie-config>
         <path>/</path>
      </cookie-config>
      ...
   </session-config>
   ```
   <br/>

   `configPath` - path to Redisson JSON or YAML config. See [configuration wiki page](https://github.com/redisson/redisson/wiki/2.-Configuration) for more details.


**2** Copy two jars into `TOMCAT_BASE/lib` directory:
  
1. __For JDK 1.8+__  
      [redisson-all-3.7.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=3.7.5&e=jar)
  
      for Tomcat 6.x  
      [redisson-tomcat-6-3.7.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-6&v=3.7.5&e=jar)  
      for Tomcat 7.x  
      [redisson-tomcat-7-3.7.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-7&v=3.7.5&e=jar)  
      for Tomcat 8.x  
      [redisson-tomcat-8-3.7.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-8&v=3.7.5&e=jar)  
      for Tomcat 9.x  
      [redisson-tomcat-9-3.7.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-9&v=3.7.5&e=jar)  
  
2. __For JDK 1.6+__  
      [redisson-all-2.12.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=2.12.5&e=jar)
  
      for Tomcat 6.x  
      [redisson-tomcat-6-2.12.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-6&v=2.12.5&e=jar)  
      for Tomcat 7.x  
      [redisson-tomcat-7-2.12.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-7&v=2.12.5&e=jar)  
      for Tomcat 8.x  
      [redisson-tomcat-8-2.12.5.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-8&v=2.12.5&e=jar)  


