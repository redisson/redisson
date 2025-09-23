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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Microsoft Entra ID (formerly Azure Active Directory) credentials resolver for Redis authentication.
 *
 * <p>This class implements the {@link CredentialsResolver} interface to provide authentication
 * credentials for connecting to Azure Cache for Redis or Azure Managed Redis instances using
 * Microsoft Entra ID authentication. It automatically handles token acquisition, refresh, and
 * authentication state management for secure, password-free Redis connections.</p>
 *
 * @author Nikita Koksharov
 *
 */
public class EntraIdCredentialsResolver implements CredentialsResolver {


    private static final Logger log = LoggerFactory.getLogger(EntraIdCredentialsResolver.class);

    final TokenManager tokenManager;

    volatile CompletableFuture<Credentials> future = new CompletableFuture<>();
    volatile CompletableFuture<Void> renewalFuture = new CompletableFuture<>();

    public EntraIdCredentialsResolver(TokenManager tokenManager) {
        this.tokenManager = tokenManager;

        TokenListener listener = new TokenListener() {

            @Override
            public void onTokenRenewed(Token token) {
                if (!future.isDone()) {
                    // update the initial future instance
                    future.complete(new Credentials(token.getUser(), token.getValue()));
                } else {
                    future = CompletableFuture.completedFuture(new Credentials(token.getUser(), token.getValue()));
                    CompletableFuture<Void> oldFuture = renewalFuture;
                    renewalFuture = new CompletableFuture<>();
                    oldFuture.complete(null);
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
    public CompletionStage<Void> nextRenewal() {
        return renewalFuture;
    }

}
