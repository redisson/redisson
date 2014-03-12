package com.lambdaworks.redis.protocol;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@SuppressWarnings("UnusedDeclaration")
public interface FutureWithPromise<T> extends Future<T> {
    CompletableFuture<T> getPromise();
}
