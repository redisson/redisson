/**
 * Copyright (c) 2013-2021 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quarkus.redisson.client.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.redisson.client.runtime.RedissonClientProducer;
import io.quarkus.redisson.client.runtime.RedissonClientRecorder;
import io.quarkus.runtime.Quarkus;
import org.eclipse.microprofile.config.ConfigProvider;
import org.redisson.config.PropertiesConvertor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author Nikita Koksharov
 *
 */
class QuarkusRedissonClientProcessor {

    private static final String FEATURE = "redisson";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem sslNativeSupport() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem addProducer() {
        return AdditionalBeanBuildItem.unremovableOf(RedissonClientProducer.class);
    }

    @BuildStep
    void addConfig(BuildProducer<NativeImageResourceBuildItem> nativeResources,
                   BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
                   BuildProducer<RuntimeInitializedClassBuildItem> staticItems,
                   BuildProducer<ReflectiveClassBuildItem> reflectiveItems) {
        nativeResources.produce(new NativeImageResourceBuildItem("redisson.yaml"));
        nativeResources.produce(new NativeImageResourceBuildItem("META-INF/services/org.jboss.marshalling.ProviderDescriptor"));
        watchedFiles.produce(new HotDeploymentWatchedFileBuildItem("redisson.yaml"));

        if (Quarkus.class.getPackage().getImplementationVersion().startsWith("1.")) {
            staticItems.produce(new RuntimeInitializedClassBuildItem("io.quarkus.redisson.client.runtime.graal.NetUtilSubstitutions$NetUtilLocalhost4LazyHolder"));
            staticItems.produce(new RuntimeInitializedClassBuildItem("io.quarkus.redisson.client.runtime.graal.NetUtilSubstitutions$NetUtilLocalhost6LazyHolder"));
            staticItems.produce(new RuntimeInitializedClassBuildItem("io.quarkus.redisson.client.runtime.graal.NetUtilSubstitutions$NetUtilLocalhostLazyHolder"));
        }

        staticItems.produce(new RuntimeInitializedClassBuildItem("io.netty.resolver.HostsFileEntriesResolver"));
        staticItems.produce(new RuntimeInitializedClassBuildItem("io.netty.resolver.dns.DnsNameResolver"));
        staticItems.produce(new RuntimeInitializedClassBuildItem("io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider"));
        staticItems.produce(new RuntimeInitializedClassBuildItem("io.netty.resolver.dns.DnsServerAddressStreamProviders$DefaultProviderHolder"));

        reflectiveItems.produce(new ReflectiveClassBuildItem(false, false, "org.redisson.codec.MarshallingCodec"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(false, false, "org.jboss.marshalling.river.RiverProviderDescriptor"));

        reflectiveItems.produce(new ReflectiveClassBuildItem(true, false, "org.redisson.executor.RemoteExecutorService"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(true, false, "org.redisson.executor.RemoteExecutorServiceAsync"));

        reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, "org.redisson.config.Config"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, "org.redisson.config.BaseConfig"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, "org.redisson.config.BaseMasterSlaveServersConfig"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, "org.redisson.config.SingleServerConfig"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, "org.redisson.config.ReplicatedServersConfig"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, "org.redisson.config.SentinelServersConfig"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, "org.redisson.config.ClusterServersConfig"));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    RedissonClientItemBuild build(RedissonClientRecorder recorder) throws IOException {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("redisson.yaml");
        if (stream != null) {
            byte[] array = new byte[stream.available()];
            stream.read(array);
            recorder.configureRedisson(new String(array, StandardCharsets.UTF_8));
        } else {
            String yaml = PropertiesConvertor.toYaml("quarkus.redisson.", ConfigProvider.getConfig().getPropertyNames(), prop -> {
                return ConfigProvider.getConfig().getValue(prop, String.class);
            });
            recorder.configureRedisson(yaml);
        }

        return new RedissonClientItemBuild();
    }

}
