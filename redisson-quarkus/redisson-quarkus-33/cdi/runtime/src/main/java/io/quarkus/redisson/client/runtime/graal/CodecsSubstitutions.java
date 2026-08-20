/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package io.quarkus.redisson.client.runtime.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DuplexChannel;
import io.netty.resolver.AddressResolverGroup;
import org.redisson.client.RedisClientConfig;
import org.redisson.config.Config;

import java.net.InetSocketAddress;

@TargetClass(className = "org.redisson.connection.ServiceManager")
final class ServiceManagerSubstitute {

    @Substitute
    private static EventLoopGroup createIOUringGroup(Config cfg) {
        throw new IllegalArgumentException("IOUring isn't compatible with native mode");
    }

    @Substitute
    private static Class<? extends DuplexChannel> createIOUringChannel() {
        throw new IllegalArgumentException("IOUring isn't compatible with native mode");
    }

    @Substitute
    private static AddressResolverGroup<InetSocketAddress> createIOUringResolver(Config cfg) {
        throw new IllegalArgumentException("IOUring isn't compatible with native mode");
    }

}

@TargetClass(className = "org.redisson.client.RedisClient")
final class RedisClientSubstitute {

    @Substitute
    private static void applyIoUringSettings(RedisClientConfig config, Bootstrap bootstrap) {
        throw new IllegalArgumentException("IOUring isn't compatible with native mode");
    }

}