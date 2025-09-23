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
package org.redisson.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.authentication.core.Token;
import redis.clients.authentication.core.TokenListener;
import redis.clients.authentication.core.TokenManager;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class EntraIdCredentialsResolver implements CredentialsResolver {


    private static final Logger log = LoggerFactory.getLogger(EntraIdCredentialsResolver.class);

    final TokenManager tokenManager;
    final Set<Runnable> callbacks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    volatile CompletableFuture<Credentials> future = new CompletableFuture<>();

    public EntraIdCredentialsResolver(TokenManager tokenManager) {
        this.tokenManager = tokenManager;

        TokenListener listener = new TokenListener() {

            @Override
            public void onTokenRenewed(Token token) {
                if (!future.isDone()) {
                    future.complete(new Credentials(token.getUser(), token.getValue()));
                } else {
                    future = CompletableFuture.completedFuture(new Credentials(token.getUser(), token.getValue()));
                }

                for (Runnable callback : callbacks) {
                    callback.run();
                }
            }

            @Override
            public void onError(Exception e) {
                log.error("Unable to renew token", e);
            }
        };

        try {
            tokenManager.start(listener, false);
        } catch (Exception e) {
            CompletableFuture<Credentials> cf = new CompletableFuture<>();
            cf.completeExceptionally(e);
            future = cf;
            tokenManager.stop();
            throw new IllegalStateException("Unable to start", e);
        }
    }

    @Override
    public CompletionStage<Credentials> resolve(InetSocketAddress address) {
        return future;
    }

    @Override
    public void addRenewAuthCallback(Runnable callback) {
        callbacks.add(callback);
    }

    @Override
    public void removeRenewAuthCallback(Runnable callback) {
        callbacks.remove(callback);
    }
}
