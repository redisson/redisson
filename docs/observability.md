## Metrics

_This feature is available only in [Redisson PRO](https://redisson.pro)_

### Monitoring systems integration

Redisson provides integration the most popular monitoring systems through [Micrometer](https://micrometer.io/) framework.

**1. AppOptics**  

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-appoptics`

Class: `org.redisson.config.metrics.AppOpticsMeterRegistryProvider`

Parameters:

* `uri` - AppOptics host uri  
* `hostTag` - tag mapped to host  
* `apiToken` - AppOptics api token  
* `numThreads` - number of threads used by scheduler (default is 2)  
* `step` - update interval in ISO-8601 format (default is 1 min)  
* `batchSize` - number of measurements sent per request (default is 500) 

**2. Atlas**  

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-atlas`

Class: `org.redisson.config.metrics.AtlasMeterRegistryProvider`

Parameters:

* `uri` - Atlas host uri
* `configUri` - Atlas LWC endpoint uri to retrieve current subscriptions
* `evalUri` - Atlas LWC endpoint uri to evaluate the data for a subscription
* `numThreads` - number of threads used by scheduler (default is 4)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 10000)

**3. Azure**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-azure-monitor`

Class: `org.redisson.config.metrics.AzureMonitorMeterRegistryProvider`

Parameters:

* `instrumentationKey` - instrumentation key
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500) 

**4. Amazon CloudWatch**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-cloudwatch`

Class: `org.redisson.config.metrics.CloudWatchMeterRegistryProvider`

Parameters:

* `accessKey` - AWS access key
* `secretKey` - AWS secret access key
* `namespace` - namespace value
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**5. Datadog**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-datadog`

Class: `org.redisson.config.metrics.DatadogMeterRegistryProvider`

Parameters:

* `uri` - Datadog host uri
* `hostTag` - tag mapped to host
* `apiKey` - api key
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**6. Dropwizard**

Class: `org.redisson.config.metrics.DropwizardMeterRegistryProvider`

Parameters:

* `sharedRegistryName` - name used to store instance in `SharedMetricRegistries`
* `nameMapper` - custom implementation of `io.micrometer.core.instrument.util.HierarchicalNameMapper`

**7. Dynatrace**

Class: `org.redisson.config.metrics.DynatraceMeterRegistryProvider`

Parameters:

* `uri` - Dynatrace host uri
* `apiToken` - api token
* `deviceId` - device id
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**8. Elastic**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-elastic`

Class: `org.redisson.config.metrics.ElasticMeterRegistryProvider`

Parameters:

* `host` - Elasticsearch host uri
* `userName` - user name
* `password` - password
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**9. Ganglia**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-ganglia`

Class: `org.redisson.config.metrics.GangliaMeterRegistryProvider`

Parameters:

* `host` - Ganglia host address
* `port` - Ganglia port
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**10. Graphite**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-graphite`

Class: `org.redisson.config.metrics.GraphiteMeterRegistryProvider`

Parameters:

* `host` - Graphite host address
* `port` - Graphite port

**11. Humio**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-humio`

Class: `org.redisson.config.metrics.HumioMeterRegistryProvider`

Parameters:

* `uri` - Humio host uri  
* `repository` - repository name  
* `apiToken` - api token  
* `numThreads` - number of threads used by scheduler (default is 2)  
* `step` - update interval in ISO-8601 format (default is 1 min)  
* `batchSize` - number of measurements sent per request (default is 500)  

**12. Influx**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-influx`

Class: `org.redisson.config.metrics.InfluxMeterRegistryProvider`

Parameters:

* `uri` - Influx host uri
* `db` - db name
* `userName` - user name
* `password` - password
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**13. JMX**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-jmx`

Class: `org.redisson.config.metrics.JmxMeterRegistryProvider`

Parameters:

* `domain` - domain name
* `sharedRegistryName` - name used to store instance in `SharedMetricRegistries`

**14. Kairos**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-kairos`

Class: `org.redisson.config.metrics.KairosMeterRegistryProvider`

Parameters:

* `uri` - Kairos host uri
* `userName` - user name
* `password` - password
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**15. NewRelic**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-new-relic`

Class: `org.redisson.config.metrics.NewRelicMeterRegistryProvider`

Parameters:

* `uri` - NewRelic host uri
* `apiKey` - api key
* `accountId` - account id
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**16. Prometheus**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-prometheus`

Class: `org.redisson.config.metrics.MeterRegistryWrapper`

Parameters:

* `registry` - instance of `PrometheusMeterRegistry` object

**17. SingnalFx**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-signalfx`

Class: `org.redisson.config.metrics.SingnalFxMeterRegistryProvider`

Parameters:

* `apiHost` - SingnalFx host uri
* `accessToken` - access token
* `source` - application instance id
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 10 secs)
* `batchSize` - number of measurements sent per request (default is 500)

**18. Stackdriver**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-stackdriver`

Class: `org.redisson.config.metrics.StackdriverMeterRegistryProvider`

Parameters:

* `projectId` - project id
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

**19. Statsd**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-statsd`

Class: `org.redisson.config.metrics.StatsdMeterRegistryProvider`

Parameters:

* `host` - Statsd host address
* `port` - Statsd port
* `flavor` - metrics format ETSY/DATADOG/TELEGRAF/SYSDIG

**20. Wavefront**

Required dependency:

* groupId: `io.micrometer`
* artifactId: `micrometer-registry-wavefront`

Class: `org.redisson.config.metrics.WavefrontMeterRegistryProvider`

Parameters:

* `uri` - Wavefront host uri
* `source` - application instance id
* `apiToken` - api token
* `numThreads` - number of threads used by scheduler (default is 2)
* `step` - update interval in ISO-8601 format (default is 1 min)
* `batchSize` - number of measurements sent per request (default is 500)

### Config examples

**JMX config**

```java
Config config = ... // Redisson PRO config object

JmxMeterRegistryProvider provider = new JmxMeterRegistryProvider();
provider.setDomain("appStats");
config.setMeterRegistryProvider(provider);
```

**Prometheus config**

```java
Config config = ... // Redisson PRO config object

PrometheusMeterRegistry registry = ...
config.setMeterRegistryProvider(new MeterRegistryWrapper(registry));
```

**Dynatrace config**

```java
Config config = ... // Redisson PRO config object

DynatraceMeterRegistryProvider p = new DynatraceMeterRegistryProvider();
p.setApiToken("Hg3M0iadsQC2Pcjk6QIW0g");
p.setUri("https://qtd9012301.live.dynatrace.com/");
p.setDeviceId("myHost");
config.setMeterRegistryProvider(p);
```

**Influx config**

```java
Config config = ... // Redisson PRO config object

InfluxMeterRegistryProvider provider = new InfluxMeterRegistryProvider();
provider.setUri("http://localhost:8086/");
provider.setDb("myinfluxdb");
provider.setUserName("admin");
provider.setPassword("admin");
config.setMeterRegistryProvider(provider);
```

### YAML config examples

YAML config is appended to Redisson config.

**JMX config**
```yaml
meterRegistryProvider: !<org.redisson.config.metrics.JmxMeterRegistryProvider> 
               domain: "appStats"
```

**Dynatrace config**
```yaml
meterRegistryProvider: !<org.redisson.config.metrics.DynatraceMeterRegistryProvider>
             apiToken: "Hg3M0iadsQC2Pcjk6QIW0g"
                  uri: "https://qtd9012301.live.dynatrace.com"
             deviceId: "myHost"
```

**Influx config**
```yaml
meterRegistryProvider: !<org.redisson.config.metrics.InfluxMeterRegistryProvider>
                  uri: "http://localhost:8086/"
                   db: "myinfluxdb"
             userName: "admin"
             password: "admin"
```

### Metrics list

The following metrics are available:

**Configuration metrics**

* `redisson.license.expiration-year` - A Gauge of the number of the license expiration year  
* `redisson.license.expiration-month` - A Gauge of the number of the license expiration month  
* `redisson.license.expiration-day` - A Gauge of the number of the license expiration day  
* `redisson.license.active-instances` - A Gauge of the number of active Redisson PRO clients  
* `redisson.executor-pool-size` - A Gauge of the number of executor threads pool size  
* `redisson.netty-pool-size` - A Gauge of the number of netty threads pool size  
* `redisson.netty-tasks-pending` - A Gauge of pending tasks in Netty event executor

**Metrics per Redis or Valkey node**

Base name: `redisson.redis.<host>:<port>`

* `status` - A Gauge of the number value of Redis or Valkey node status [1 = connected, -1 = disconnected]  
* `type` - A Gauge of the number value of Redis or Valkey node type [1 = MASTER, 2 = SLAVE, 3 = SENTINEL]  
<br/>

* `total-response-bytes` - A Meter of the total amount of bytes received from Redis or Valkey  
* `response-bytes` - A Histogram of the number of bytes received from Redis or Valkey  
* `total-request-bytes` - A Meter of the total amount of bytes sent to Redis or Valkey  
* `request-bytes` - A Histogram of the number of bytes sent to Redis or Valkey  
<br/>

* `connections.active` - A Counter of the number of busy connections  
* `connections.free` - A Counter of the number of free connections  
* `connections.max-pool-size` - A Counter of the number of maximum connection pool size  
* `connections.reconnected` - A Counter of the number of reconnected connections  
* `connections.total` - A Counter of the number of total connections in pool  
<br/>

* `publish-subscribe-connections.active` - A Counter of the number of active publish subscribe connections  
* `publish-subscribe-connections.free` - A Counter of the number of free publish subscribe connections  
* `publish-subscribe-connections.max-pool-size` - A Counter of the number of maximum publish subscribe connection pool size  
* `publish-subscribe-connections.total` - A Counter of the number of total publish subscribe connections in pool  
<br/>

* `operations.total` - A Meter of the number of total executed operations  
* `operations.total-failed` - A Meter of the number of total failed operations  
* `operations.total-successful` - A Meter of the number of total successful operations  
* `operations.latency` - A Histogram of the number of operations latency in milliseconds  
* `operations.retry-attempt` - A Histogram of the number of operations retry attempts  

**Metrics per RRemoteService object**

Base name: `redisson.remote-service.<name>`

* `invocations.total` - A Meter of the number of total executed invocations  
* `invocations.total-failed` - A Meter of the number of total failed to execute invocations  
* `invocations.total-successful` - A Meter of the number of total successful to execute invocations  

**Metrics per RExecutorService object**

Base name: `redisson.executor-service.<name>`

* `tasks.submitted` - A Meter of the number of submitted tasks  
* `tasks.executed` - A Meter of the number of executed tasks  
<br/>

* `workers.active` - A Gauge of the number of busy task workers  
* `workers.free` - A Gauge of the number of free task workers  
* `workers.total` - A Gauge of the number of total task workers  
* `workers.tasks-executed.total` - A Meter of the number of total executed tasks by workers  
* `workers.tasks-executed.total-failed` - A Meter of the number of total failed to execute tasks by workers  
* `workers.tasks-executed.total-successful` - A Meter of the number of total successful to execute tasks by workers  

**Metrics per RMap object**

Base name: `redisson.map.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  

**Metrics per RMapCache object**

Base name: `redisson.map-cache.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  

**Metrics per RMapCacheV2 object**

Base name: `redisson.map-cache-v2.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  

**Metrics per RMapCacheNative object**

Base name: `redisson.map-cache-native.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  

**Metrics per RClusteredMapCache object**

Base name: `redisson.clustered-map-cache.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  

**Metrics per RClusteredMapCacheNative object**

Base name: `redisson.clustered-map-cache-native.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  


**Metrics per RLocalCachedMap object**

Base name: `redisson.local-cached-map.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  
<br/>

* `local-cache.hits` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.misses` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.evictions` - A Meter of the number of evictions for data contained in the local cache  
* `local-cache.size` - A Gauge of the number of local cache size  

**Metrics per RClusteredLocalCachedMap object**

Base name: `redisson.clustered-local-cached-map.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  
<br/>

* `local-cache.hits` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.misses` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.evictions` - A Meter of the number of evictions for data contained in the local cache  
* `local-cache.size` - A Gauge of the number of local cache size  

**Metrics per RLocalCachedMapCache object**

Base name: `redisson.local-cached-map-cache.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  
<br/>

* `local-cache.hits` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.misses` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.evictions` - A Meter of the number of evictions for data contained in the local cache  
* `local-cache.size` - A Gauge of the number of local cache size  

**Metrics per RLocalCachedMapCacheV2 object**

Base name: `redisson.local-cached-map-cache-v2.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  
<br/>

* `local-cache.hits` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.misses` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.evictions` - A Meter of the number of evictions for data contained in the local cache  
* `local-cache.size` - A Gauge of the number of local cache size  

**Metrics per RLocalCachedMapCacheNative object**

Base name: `redisson.local-cached-map-cache-native.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  
<br/>

* `local-cache.hits` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.misses` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.evictions` - A Meter of the number of evictions for data contained in the local cache  
* `local-cache.size` - A Gauge of the number of local cache size  

**Metrics per RClusteredLocalCachedMapCache object**

Base name: `redisson.clustered-local-cached-map-cache.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  
<br/>

* `local-cache.hits` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.misses` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.evictions` - A Meter of the number of evictions for data contained in the local cache  
* `local-cache.size` - A Gauge of the number of the local cache size  

**Metrics per RJsonStore object**

Base name: `redisson.jsonstore.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  

**Metrics per RLocalCachedJsonStore object**

Base name: `redisson.local-cached-jsonstore.<name>`

* `hits` - A Meter of the number of get requests for data contained in the cache  
* `misses` - A Meter of the number of get requests for data not contained in the cache  
* `puts` - A Meter of the number of puts to the cache  
* `removals` - A Meter of the number of removals from the cache  
<br/>

* `local-cache.hits` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.misses` - A Meter of the number of get requests for data contained in the local cache  
* `local-cache.evictions` - A Meter of the number of evictions for data contained in the local cache  
* `local-cache.size` - A Gauge of the number of the local cache size  

**Metrics per RTopic object**

Base name: `redisson.topic.<name>`

* `messages-sent` - A Meter of the number of messages sent for the topic  
* `messages-received` - A Meter of the number of messages received for the topic  

**Metrics per RClusteredTopic object**

Base name: `redisson.clustered-topic.<name>`

* `messages-sent` - A Meter of the number of messages sent for the topic  
* `messages-received` - A Meter of the number of messages received for the topic  

**Metrics per RReliableTopic object**

Base name: `redisson.reliable-topic.<name>`

* `messages-sent` - A Meter of the number of messages sent for the topic  
* `messages-received` - A Meter of the number of messages received for the topic  

**Metrics per RClusteredReliableTopic object**

Base name: `redisson.clustered-reliable-topic.<name>`

* `messages-sent` - A Meter of the number of messages sent for the topic  
* `messages-received` - A Meter of the number of messages received for the topic  

**Metrics per RShardedTopic object**

Base name: `redisson.sharded-topic.<name>`

* `messages-sent` - A Meter of the number of messages sent for the topic  
* `messages-received` - A Meter of the number of messages received for the topic  

**Metrics per RBucket object**

Base name: `redisson.bucket.<name>`

* `gets` - A Meter of the number of get operations executed for the object
* `sets` - A Meter of the number of set operations executed for the object

**Metrics per JCache object**

Base name: `redisson.JCache.<name>`

* `cache.evictions` - A Meter of the number of evictions for data contained in JCache  
* `cache.puts` - A Meter of the number of puts to the JCache  
* `hit.cache.gets` - A Meter of the number of get requests for data contained in JCache  
* `miss.cache.gets` - A Meter of the number of get requests for data contained in JCache  

## Tracing

_This feature is available only in [Redisson PRO](https://redisson.pro)_

Redisson provides integration with the most popular tracer libraries through [Micrometer Observation API](https://docs.micrometer.io/micrometer/reference/observation.html) and [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/) framework. This feature allows to gather extra statistics about invoked Redisson methods: name, arguments, invocation path, duration and frequency.

Configuration metrics per Tracing event.

* `invocation` - Redisson method with arguments. Arguments are included only if `includeArgs` set `true`  
* `object_name` - Redisson object name  
* `address` - Redis or Valkey server address  
* `username` - username used to connect to Redis or Valkey server

### OpenZipkin Brave

Configuration example:

```java
OkHttpSender sender = OkHttpSender.create("http://localhost:9411/api/v2/spans");
AsyncZipkinSpanHandler zipkinSpanHandler = AsyncZipkinSpanHandler.create(sender);
StrictCurrentTraceContext braveCurrentTraceContext = StrictCurrentTraceContext.create();

Tracing tracing = Tracing.newBuilder()
                         .localServiceName("myservice")
                         .currentTraceContext(braveCurrentTraceContext)
                         .sampler(Sampler.ALWAYS_SAMPLE)
                         .addSpanHandler(zipkinSpanHandler)
                         .build();

Config config = ... // Redisson PRO config object

config.setTracingProvider(new BraveTracingProvider(tracing));
```

Required dependencies:  

1. groupId: `io.zipkin.reporter2`, artifactId: `zipkin-sender-okhttp3`  
2. groupId: `io.zipkin.reporter2`, artifactId: `zipkin-reporter-brave`

### OpenTelemetry

Configuration example:

```java
SpanExporter spanExporter = new ZipkinSpanExporterBuilder()
                                    .setSender(URLConnectionSender.create("http://localhost:9411/api/v2/spans"))
                                    .build();

SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                                           .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                                           .build();

OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                                         .setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
                                         .setTracerProvider(sdkTracerProvider)
                                         .build();

io.opentelemetry.api.trace.Tracer otelTracer = openTelemetrySdk.getTracerProvider().get("io.micrometer.micrometer-tracing");

Config config = ... // Redisson PRO config object

config.setTracingProvider(new OtelTracingProvider(otelTracer, openTelemetrySdk.getPropagators()));
```

Required dependencies:  

1. groupId: `io.opentelemetry`, artifactId: `opentelemetry-exporter-zipkin`  
2. groupId: `io.zipkin.reporter2`, artifactId: `zipkin-sender-urlconnection`


