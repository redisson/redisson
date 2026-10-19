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
package org.redisson.transaction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.api.RFuture;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonTransactionSyncSlavesTest extends RedisDockerTest {

    @Test
    public void testSyncSlavesFailure() {
        RTransaction transaction = redisson.createTransaction(
                TransactionOptions.defaults().syncSlaves(1, 1, TimeUnit.SECONDS));
        transaction.getBucket("testKey").set("testValue");

        TransactionSyncException ex = Assertions.assertThrows(TransactionSyncException.class, () -> {
            transaction.commit();
        });

        assertThat(ex.getMessage()).isEqualTo("Transaction committed, but only 0 of 1 replicas acknowledged within 1000 ms");
        assertThat(redisson.getBucket("testKey").get()).isEqualTo("testValue");
        redisson.getBucket("testKey").delete();
    }

    @Test
    public void testSyncSlavesAsyncFailure() {
        RTransaction transaction = redisson.createTransaction(
                TransactionOptions.defaults().syncSlaves(1, 1, TimeUnit.SECONDS));
        transaction.getBucket("testKeyAsync").set("testValueAsync");

        RFuture<Void> future = transaction.commitAsync();
        ExecutionException ex = Assertions.assertThrows(ExecutionException.class, future::get);
        assertThat(ex.getCause()).isInstanceOf(TransactionSyncException.class);
        assertThat(ex.getCause().getMessage()).isEqualTo("Transaction committed, but only 0 of 1 replicas acknowledged within 1000 ms");

        assertThat(redisson.getBucket("testKeyAsync").get()).isEqualTo("testValueAsync");
        redisson.getBucket("testKeyAsync").delete();
    }

    @Test
    public void testDefaultOptionsPassWithoutReplicaSync() {
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        transaction.getBucket("defaultKey").set("defaultValue");
        transaction.commit();

        assertThat(redisson.getBucket("defaultKey").get()).isEqualTo("defaultValue");
        redisson.getBucket("defaultKey").delete();
    }

    @Test
    public void testTransactionOptionsSyncSlaves() {
        TransactionOptions options = TransactionOptions.defaults()
                .syncSlaves(3, 10, TimeUnit.SECONDS);

        assertThat(options.getSyncSlaves()).isEqualTo(3);
        assertThat(options.getSyncTimeout()).isEqualTo(10000L);

        TransactionOptions optionsCopy = TransactionOptions.defaults()
                .syncSlaves(3, 10, TimeUnit.SECONDS);
        assertThat(options).isEqualTo(optionsCopy);
        assertThat(options.hashCode()).isEqualTo(optionsCopy.hashCode());

        TransactionOptions noSync = TransactionOptions.defaults()
                .syncSlaves(-1, 5, TimeUnit.SECONDS);
        assertThat(noSync.getSyncSlaves()).isEqualTo(-1);
    }

}
