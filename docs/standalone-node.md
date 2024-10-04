## Overview
Redisson offers ability to run as standalone node and participate in distributed computing. Such Nodes are used to run [MapReduce](data-and-services/services.md#mapreduce-service), [ExecutorService](data-and-services/services.md#executor-service), [ScheduledExecutorService](data-and-services/services.md#scheduled-executor-service) tasks or [RemoteService](data-and-services/services.md#remote-service) services. All tasks are kept in Redis until their execution moment.

Packaged as a single jar and could be downloaded [here](https://repo1.maven.org/maven2/org/redisson/redisson-all/).

![](https://redisson.org/architecture.png)

## Configuration
### Settings
Redisson node uses same [configuration](configuration.md) as Redisson framework with additional settings. Threads amount available for ExecutorService set through `threads` setting.

**mapReduceWorkers**

Default value: `0`

MapReduce workers amount.
`0 = current_processors_amount`

**executorServiceWorkers**

Default value: `null`

Map with <b>key</b> as service name and <b>value</b> as workers amount.

**redissonNodeInitializer**

Default value: `null`

Listener runs during Redisson node startup.

**beanFactory**

Default value: `null`

Defines Spring Bean Factory instance to execute tasks with Spring's '@Autowired', '@Value' or JSR-330's '@Inject' annotation.

### YAML config format

Below is the configuration example for cluster mode with appended Redisson node settings in YAML format.
```yaml
---
clusterServersConfig:
  nodeAddresses:
  - "//127.0.0.1:7004"
  - "//127.0.0.1:7001"
  - "//127.0.0.1:7000"
  scanInterval: 1000
threads: 0

executorServiceWorkers:
  myService1: 123
  myService2: 421
redissonNodeInitializer: !<org.mycompany.MyRedissonNodeInitializer> {}
```

## Initialization listener
Redisson node allows to execute initialization logic during startup via `RedissonNodeInitializer` listener. It allows, for example, register your remote service implementations which should be available in Redisson node's classpath, or execute other useful logic. For example, notify all subscribers about new Redisson node is available.
```java
public class MyRedissonNodeInitializer implements RedissonNodeInitializer {

    @Override
    public void onStartup(RedissonNode redissonNode) {
        RMap<String, Integer> map = redissonNode.getRedisson().getMap("myMap");
        // ...
        // or
        redisson.getRemoteService("myRemoteService").register(MyRemoteService.class, new MyRemoteServiceImpl(...));
        // or
        reidsson.getTopic("myNotificationTopic").publish("New node has joined. id:" + redissonNode.getId() + " remote-server:" + redissonNode.getRemoteAddress());
    }

}
```

## How to run
### As Embedded node
Redisson node can be embedded into your application:
```java
// Redisson config
Config config = ...
// Redisson Node config
RedissonNodeConfig nodeConfig = new RedissonNodeConfig(config);
Map<String, Integer> workers = new HashMap<String, Integer>();
workers.put("test", 1);
nodeConfig.setExecutorServiceWorkers(workers);

// create Redisson node
RedissonNode node = RedissonNode.create(nodeConfig);
// or create Redisson node with existing Redisson instance
RedissonNode node = RedissonNode.create(nodeConfig, redisson);

node.start();

//...

node.shutdown();
```

### From command-line

1. Download Redisson node jar [here](https://repo1.maven.org/maven2/org/redisson/redisson-all/)
2. Create yaml configuration file
3. Run node using follow command line:  
`java -jar redisson-all.jar config.yaml`  

don't forget to add `-Xmx` and `-Xms` params

### Using Docker

**with Redis or Valkey instance**

1. Run Redis  
   `docker run -d --name redis-node redis`
2. Redisson Node  
   `docker run -d --network container:redis-node -e JAVA_OPTS="<java-opts>" -v <path-to-config>:/opt/redisson-node/redisson.conf redisson/redisson-node`
   
   `<path-to-config>` - path to YAML configuration of Redisson Node
   `<java-opts>` - JVM params

**without Redis or Valkey instance**

1. Redisson Node  
   `docker run -d -e JAVA_OPTS="<java-opts>" -v <path-to-config>:/opt/redisson-node/redisson.conf redisson/redisson-node`
   
   `<path-to-config>` - path to YAML configuration of Redisson Node
   `<java-opts>` - JVM params
