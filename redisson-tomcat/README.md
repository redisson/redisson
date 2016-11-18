Redis based Tomcat Session Manager
===

Implements non-sticky session management backed by Redis.  
Supports Tomcat 6.x, 7.x, 8.x

Advantages
===

Current implementation differs from any other Tomcat Session Manager in terms of efficient storage and update. Each session attribute is written into Redis during each `setAttribute` invocation. While other solutions serialize whole session each time.

Usage
===
1. Add `RedissonSessionManager` into `context.xml`
   ```xml
<Manager className="org.redisson.tomcat.RedissonSessionManager"
	         configPath="${catalina.base}/redisson.conf" />
   ```
   `configPath` - path to Redisson JSON or YAML config. See [configuration wiki page](https://github.com/redisson/redisson/wiki/2.-Configuration) for more details.

2. Copy two jars into `TOMCAT_BASE/lib` directory:
  
  1. __For JDK 1.8+__  
      [redisson-all-3.2.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=3.2.0&e=jar)
  
      for Tomcat 6.x  
      [redisson-tomcat-6-3.2.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-6&v=3.2.0&e=jar)  
      for Tomcat 7.x  
      [redisson-tomcat-7-3.2.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-7&v=3.2.0&e=jar)  
      for Tomcat 8.x  
      [redisson-tomcat-8-3.2.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-8&v=3.2.0&e=jar)
  
  1. __For JDK 1.6+__  
      [redisson-all-2.7.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=2.7.0&e=jar)
  
      for Tomcat 6.x  
      [redisson-tomcat-6-2.7.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-6&v=2.7.0&e=jar)  
      for Tomcat 7.x  
      [redisson-tomcat-7-2.7.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-7&v=2.7.0&e=jar)  
      for Tomcat 8.x  
      [redisson-tomcat-8-2.7.0.jar](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-tomcat-8&v=2.7.0&e=jar)

