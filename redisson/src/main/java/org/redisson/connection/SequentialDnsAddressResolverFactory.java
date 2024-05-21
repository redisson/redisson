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
package org.redisson.connection;

import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.NameResolver;
import io.netty.resolver.dns.*;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import org.redisson.misc.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Address resolver which allows to control concurrency level of requests to DNS servers.
 *
 * @author Nikita Koksharov
 *
 */
public class SequentialDnsAddressResolverFactory implements AddressResolverGroupFactory {

    static final Logger log = LoggerFactory.getLogger(SequentialDnsAddressResolverFactory.class);

    static class LimitedInetSocketAddressResolver extends InetSocketAddressResolver {

        final AsyncSemaphore semaphore;

        LimitedInetSocketAddressResolver(AsyncSemaphore semaphore, EventExecutor executor, NameResolver<InetAddress> nameResolver) {
            super(executor, nameResolver);
            this.semaphore = semaphore;
        }

        @Override
        protected void doResolve(InetSocketAddress unresolvedAddress, Promise<InetSocketAddress> promise) throws Exception {
            execute(() -> {
                super.doResolve(unresolvedAddress, promise);
                return null;
            }, promise);
        }

        @Override
        protected void doResolveAll(InetSocketAddress unresolvedAddress, Promise<List<InetSocketAddress>> promise) throws Exception {
            execute(() -> {
                super.doResolveAll(unresolvedAddress, promise);
                return null;
            }, promise);
        }

        private void execute(Callable<?> callable, Promise<?> promise) {
            semaphore.acquire().thenAccept(s -> {
                promise.addListener(r -> {
                    semaphore.release();
                });
                try {
                    callable.call();
                } catch (Exception e) {
                    promise.setFailure(e);
                }
            });
        }
    }

    private final AsyncSemaphore asyncSemaphore;

    public SequentialDnsAddressResolverFactory() {
        this(2);
    }

    /**
     * Creates DNS resolver factory with the specified number of requests
     * to DNS servers which can be executed at the same moment.
     *
     * @param concurrencyLevel number of requests can be executed at the same moment.
     */
    public SequentialDnsAddressResolverFactory(int concurrencyLevel) {
        asyncSemaphore = new AsyncSemaphore(concurrencyLevel);
    }

    @Override
    public AddressResolverGroup<InetSocketAddress> create(Class<? extends DatagramChannel> channelType,
                                                          Class<? extends SocketChannel> socketChannelType,
                                                          DnsServerAddressStreamProvider nameServerProvider) {
        DnsNameResolverBuilder dnsResolverBuilder = new DnsNameResolverBuilder();
        try {
            dnsResolverBuilder.getClass().getMethod("socketChannelType", Class.class, boolean.class);
            dnsResolverBuilder.socketChannelType(socketChannelType, true);
        } catch (NoSuchMethodException e) {
            log.warn("DNS TCP fallback on UDP query timeout disabled. Upgrade Netty to 4.1.105 or higher.");
            dnsResolverBuilder.socketChannelType(socketChannelType);
        }
        dnsResolverBuilder.channelType(channelType)
                        .nameServerProvider(nameServerProvider)
                        .resolveCache(new DefaultDnsCache())
                        .cnameCache(new DefaultDnsCnameCache());

        DnsAddressResolverGroup group = new DnsAddressResolverGroup(dnsResolverBuilder) {
            @Override
            protected AddressResolver<InetSocketAddress> newAddressResolver(EventLoop eventLoop, NameResolver<InetAddress> resolver) throws Exception {
                return new LimitedInetSocketAddressResolver(asyncSemaphore, eventLoop, resolver);
            }
        };
        return group;
    }
}
