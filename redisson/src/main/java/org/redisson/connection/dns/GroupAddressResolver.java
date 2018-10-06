/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.connection.dns;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.resolver.AddressResolver;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

/**
 * Workaround for https://github.com/netty/netty/issues/8261
 * 
 * @author Nikita Koksharov
 *
 */
class GroupAddressResolver implements AddressResolver<InetSocketAddress> {

    private final List<AddressResolver<InetSocketAddress>> resolvers;
    
    public GroupAddressResolver(List<AddressResolver<InetSocketAddress>> resolvers) {
        super();
        this.resolvers = resolvers;
    }

    @Override
    public boolean isSupported(SocketAddress address) {
        for (AddressResolver<InetSocketAddress> addressResolver : resolvers) {
            if (addressResolver.isSupported(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isResolved(SocketAddress address) {
        for (AddressResolver<InetSocketAddress> addressResolver : resolvers) {
            if (addressResolver.isResolved(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Future<InetSocketAddress> resolve(SocketAddress address) {
        final Promise<InetSocketAddress> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        final AtomicInteger counter = new AtomicInteger(resolvers.size());
        for (AddressResolver<InetSocketAddress> addressResolver : resolvers) {
            addressResolver.resolve(address).addListener(new FutureListener<InetSocketAddress>() {
                @Override
                public void operationComplete(Future<InetSocketAddress> future) throws Exception {
                    if (future.isSuccess()) {
                        promise.trySuccess(future.getNow());
                    }
                    
                    if (counter.decrementAndGet() == 0) {
                        if (!future.isSuccess()) {
                            promise.tryFailure(future.cause());
                        }
                    }
                }
            });
        }
        return promise;
    }

    @Override
    public Future<InetSocketAddress> resolve(SocketAddress address, Promise<InetSocketAddress> promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) {
        final Promise<List<InetSocketAddress>> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        final AtomicInteger counter = new AtomicInteger(resolvers.size());
        for (AddressResolver<InetSocketAddress> addressResolver : resolvers) {
            addressResolver.resolveAll(address).addListener(new FutureListener<List<InetSocketAddress>>() {
                @Override
                public void operationComplete(Future<List<InetSocketAddress>> future) throws Exception {
                    if (future.isSuccess()) {
                        promise.trySuccess(future.getNow());
                    }
                    
                    if (counter.decrementAndGet() == 0) {
                        if (!future.isSuccess()) {
                            promise.tryFailure(future.cause());
                        }
                    }
                }
            });
        }
        return promise;
    }

    @Override
    public Future<List<InetSocketAddress>> resolveAll(SocketAddress address, Promise<List<InetSocketAddress>> promise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
    }

}
