/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.RedissonBucket;
import org.redisson.RedissonMultimap;
import org.redisson.RedissonObject;
import org.redisson.api.RBucket;
import org.redisson.api.RExpirable;
import org.redisson.api.RObject;
import org.redisson.api.RObjectReactive;
import org.redisson.codec.Kryo5Codec;
import org.redisson.config.*;
import org.redisson.executor.RemoteExecutorService;
import org.redisson.executor.RemoteExecutorServiceAsync;

import java.io.IOException;

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

        reflectiveItems.produce(ReflectiveClassBuildItem.builder(Kryo5Codec.class)
                .methods(false)
                .fields(false)
                .build()
        );

        reflectiveItems.produce(ReflectiveClassBuildItem.builder(
                        RemoteExecutorService.class,
                        RemoteExecutorServiceAsync.class)
                .methods(true)
                .fields(false)
                .build()
        );

        reflectiveItems.produce(ReflectiveClassBuildItem.builder(
                        Config.class,
                        BaseConfig.class,
                        BaseMasterSlaveServersConfig.class,
                        SingleServerConfig.class,
                        ReplicatedServersConfig.class,
                        SentinelServersConfig.class,
                        ClusterServersConfig.class)
                .methods(true)
                .fields(true)
                .build()
        );

        reflectiveItems.produce(ReflectiveClassBuildItem.builder(
                        RBucket.class,
                        RedissonBucket.class,
                        RedissonObject.class,
                        RedissonMultimap.class)
                .methods(true)
                .fields(true)
                .build()
        );

        reflectiveItems.produce(ReflectiveClassBuildItem.builder(
                        RObjectReactive.class,
                        RExpirable.class,
                        RObject.class)
                .methods(true)
                .build()
        );
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    RedissonClientItemBuild build(RedissonClientRecorder recorder) throws IOException {
        recorder.createProducer();
        return new RedissonClientItemBuild();
    }

}
