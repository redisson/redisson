Redis based Tomcat Session Manager
===

Stores session of Apache Tomcat in Redis and allows to distribute requests across a cluster of Tomcat servers. Implements non-sticky session management backed by Redis.  

Supports Apache Tomcat 6.x, 7.x, 8.x, 9.x

Advantages
===

Current implementation differs from any other Redis based Tomcat Session Manager in terms of efficient storage and optimized writes. Each session attribute is written into Redis during each `HttpSession.setAttribute` invocation. While other solutions serialize whole session each time.

Usage
===

**1** Add `RedissonSessionManager` into `context.xml`
   
   ```xml
<Manager className="org.redisson.tomcat.RedissonSessionManager"
	         configPath="${catalina.base}/redisson.conf" readMode="MEMORY" updateMode="DEFAULT"/>
   ```
   `readMode` - read attributes mode. Two modes are available:
   * `MEMORY` - read attributes stored in local Tomcat Session. Default mode.
   * `REDIS` - read directly from Redis.  

   `updateMode` - attributes update mode. Two modes are available:
   * `DEFAULT` - session attributes are stored into Redis only through setAttribute method. Default mode.
   * `AFTER_REQUEST` - all session attributes are stored into Redis after each request.

   `configPath` - path to Redisson JSON or YAML config. See [configuration wiki page](https://github.com/redisson/redisson/wiki/2.-Configuration) for more details.


**2** Copy two jars into `TOMCAT_BASE/lib` directory:
  
1. __For JDK 1.8+__  
      [redisson-all-3.6.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=3.6.0&e=jar)
  
      for Tomcat 6.x  
      [redisson-tomcat-6-3.6.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-6&v=3.6.0&e=jar)  
      for Tomcat 7.x  
      [redisson-tomcat-7-3.6.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-7&v=3.6.0&e=jar)  
      for Tomcat 8.x  
      [redisson-tomcat-8-3.6.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-8&v=3.6.0&e=jar)  
      for Tomcat 9.x  
      [redisson-tomcat-9-3.6.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-9&v=3.6.0&e=jar)  
  
2. __For JDK 1.6+__  
      [redisson-all-2.11.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=2.11.0&e=jar)
  
      for Tomcat 6.x  
      [redisson-tomcat-6-2.11.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-6&v=2.11.0&e=jar)  
      for Tomcat 7.x  
      [redisson-tomcat-7-2.11.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-7&v=2.11.0&e=jar)  


