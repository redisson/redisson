package org.redisson.spring.starter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = RedissonApplication.class,
        properties = {
                "spring.redis.redisson.useSpringBootConfiguration=false",

                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.sentinelAddresses=[redis://sentinel-server1:6379, redis://sentinel-server2:6379]",
                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.masterName=master-name",
                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.database=0",
                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.scanInterval=1000",
                // "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.natMapper=value",
                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.checkSentinelsList=true",
                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.sentinelUsername=username",
                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.sentinelPassword=password",
                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.checkSlaveStatusWithSyncing=true",
                "spring.redis.redisson.springBootConfiguration.sentinelServersConfig.sentinelsDiscovery=true",

                // Spring Boot does not allow custom YAML types: https://github.com/spring-projects/spring-boot/issues/21596
                // In a redisson-specific YAML you could set this value to "!<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}"
                // Now it requires a Converter from String (FQN) to instance.
                "spring.redis.redisson.springBootConfiguration.masterSlaveServersConfig.loadBalancer=org.redisson.connection.balancer.RoundRobinLoadBalancer",
                "spring.redis.redisson.springBootConfiguration.masterSlaveServersConfig.masterAddress=redis://master-address:6379",
                "spring.redis.redisson.springBootConfiguration.masterSlaveServersConfig.slaveAddresses=[redis://slave-address1:6379, redis://slave-address2:6379]",
                "spring.redis.redisson.springBootConfiguration.masterSlaveServersConfig.database=0",

                "spring.redis.redisson.springBootConfiguration.singleServerConfig.address=redis://single-server:6379",
                "spring.redis.redisson.springBootConfiguration.singleServerConfig.connectionPoolSize=64",
                "spring.redis.redisson.springBootConfiguration.singleServerConfig.subscriptionConnectionPoolSize=50",
                "spring.redis.redisson.springBootConfiguration.singleServerConfig.dnsMonitoringInterval=5000",
                "spring.redis.redisson.springBootConfiguration.singleServerConfig.subscriptionConnectionMinimumIdleSize=1",
                "spring.redis.redisson.springBootConfiguration.singleServerConfig.connectionMinimumIdleSize=24",
                "spring.redis.redisson.springBootConfiguration.singleServerConfig.database=0",

                "spring.redis.redisson.springBootConfiguration.clusterServersConfig.nodeAddresses=[redis://cluster-server1:6379,redis://cluster-server2:6379]",
                "spring.redis.redisson.springBootConfiguration.clusterServersConfig.scanInterval=5000",
                // "spring.redis.redisson.springBootConfiguration.clusterServersConfig.natMapper=0",
                "spring.redis.redisson.springBootConfiguration.clusterServersConfig.checkSlotsCoverage=true",

                "spring.redis.redisson.springBootConfiguration.replicatedServersConfig.nodeAddresses=[redis://replicated-server1:6379, redis://replicated-server2:6379]",
                "spring.redis.redisson.springBootConfiguration.replicatedServersConfig.scanInterval=1000",
                "spring.redis.redisson.springBootConfiguration.replicatedServersConfig.database=0",

                // "spring.redis.redisson.springBootConfiguration.connectionManager=0",

                "spring.redis.redisson.springBootConfiguration.threads=16",
                "spring.redis.redisson.springBootConfiguration.nettyThreads=32",
                // Spring Boot does not allow custom YAML types: https://github.com/spring-projects/spring-boot/issues/21596
                // In a redisson-specific YAML you could set this value to "!<org.redisson.codec.JsonJacksonCodec> {}"
                // Now it requires a Converter from String (FQN) to instance.
                "spring.redis.redisson.springBootConfiguration.codec=org.redisson.codec.JsonJacksonCodec",
                // "spring.redis.redisson.springBootConfiguration.executor=0",
                "spring.redis.redisson.springBootConfiguration.referenceEnabled=true",
                // "spring.redis.redisson.springBootConfiguration.transportMode=0",
                // "spring.redis.redisson.springBootConfiguration.eventLoopGroup=0",
                "spring.redis.redisson.springBootConfiguration.lockWatchdogTimeout=30000",
                // "spring.redis.redisson.springBootConfiguration.reliableTopicWatchdogTimeout=true",
                "spring.redis.redisson.springBootConfiguration.keepPubSubOrder=true",
                "spring.redis.redisson.springBootConfiguration.useScriptCache=false",
                "spring.redis.redisson.springBootConfiguration.minCleanUpDelay=5",
                "spring.redis.redisson.springBootConfiguration.maxCleanUpDelay=1800",
                "spring.redis.redisson.springBootConfiguration.cleanUpKeysAmount=100",
                // "spring.redis.redisson.springBootConfiguration.nettyHook=0",
                // "spring.redis.redisson.springBootConfiguration.connectionListener=0",
                "spring.redis.redisson.springBootConfiguration.useThreadClassLoader=true",
                // "spring.redis.redisson.springBootConfiguration.addressResolverGroupFactory=0",
        })
public class RedissonPropertiesTest {
    @Autowired
    private RedissonProperties redissonProperties;

    @Test
    void readSpringBootConfigurationCorrectly() {
        Assertions.assertThat(redissonProperties.getSpringBootConfiguration()).isNotNull();
    }
}
