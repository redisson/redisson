Redis based Tomcat Session Manager
===

Implements non-sticky session management backed by Redis.  
Supports Tomcat 6.x, 7.x, 8.x

Usage
===
1. Add `RedissonSessionManager` into `context.xml`
   ```xml
<Manager className="org.redisson.tomcat.RedissonSessionManager"
	         configPath="${catalina.base}/redisson.conf" />
   ```
   `configPath` - path to Redisson JSON or YAML config

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

