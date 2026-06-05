## Transactions management

`RMap`, `RMapCache`, `RLocalCachedMap`, `RSet`, `RSetCache` and `RBucket` objects can participate in a transaction with ACID properties. The transaction takes locks for write operations and maintains a list of data-modification operations until `commit()` is called; the acquired locks are released after `commit()` or `rollback()`. If anything goes wrong during commit or rollback, an `org.redisson.transaction.TransactionException` is thrown.

The transaction isolation level is `READ_COMMITTED`.

The following transaction options are available:

```java
TransactionOptions options = TransactionOptions.defaults()
    // Data synchronization timeout between the master participating in the transaction and its replicas.
    // Default is 5000 milliseconds.
    .syncSlavesTimeout(5, TimeUnit.SECONDS)

    // Response timeout.
    // Default is 3000 milliseconds.
    .responseTimeout(3, TimeUnit.SECONDS)

    // Interval between attempts to send the transaction if it hasn't been sent yet.
    // Default is 1500 milliseconds.
    .retryInterval(2, TimeUnit.SECONDS)

    // Number of attempts to send the transaction if it hasn't been sent yet.
    // Default is 3 attempts.
    .retryAttempts(3)

    // If the transaction isn't committed within this timeout it is rolled back automatically.
    // Default is 5000 milliseconds.
    .timeout(5, TimeUnit.SECONDS);
```

In a Valkey or Redis cluster, use `{}` hash-tag braces to keep the keys on the same slot, otherwise the server throws a CROSSSLOT error:

```java
RMap<String, String> map = transaction.getMap("myMap{user:1}");
map.put("1", "2");
String value = map.get("3");
RSet<String> set = transaction.getSet("mySet{user:1}");
set.add(value);
```

The transaction is created from the client, the objects are obtained from it, and the changes are applied with `commit()` or discarded with `rollback()`:

=== "Sync / Async"
    ```java
    RedissonClient redisson = Redisson.create(config);
    RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
    
    RMap<String, String> map = transaction.getMap("myMap");
    map.put("1", "2");
    String value = map.get("3");
    RSet<String> set = transaction.getSet("mySet");
    set.add(value);
    
    try {
        transaction.commit();
    } catch (TransactionException e) {
        transaction.rollback();
    }
    
    // or
    
    RFuture<Void> future = transaction.commitAsync();
    future.exceptionally(exception -> {
        transaction.rollbackAsync();
        return null;
    });
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = Redisson.create(config).reactive();
    RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults());
    
    RMapReactive<String, String> map = transaction.getMap("myMap");
    map.put("1", "2");
    Mono<String> value = map.get("3");
    RSetReactive<String> set = transaction.getSet("mySet");
    set.add("someValue");
    
    Mono<Void> mono = transaction.commit();
    mono.onErrorResume(exception -> transaction.rollback()).subscribe();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = Redisson.create(config).rxJava();
    RTransactionRx transaction = redisson.createTransaction(TransactionOptions.defaults());
    
    RMapRx<String, String> map = transaction.getMap("myMap");
    map.put("1", "2");
    Maybe<String> value = map.get("3");
    RSetRx<String> set = transaction.getSet("mySet");
    set.add("someValue");
    
    Completable res = transaction.commit();
    res.onErrorResumeNext(exception -> transaction.rollback()).subscribe();
    ```

## XA Transactions

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Redisson provides an [XAResource](https://docs.oracle.com/javaee/7/api/javax/transaction/xa/XAResource.html) implementation to participate in JTA transactions. `RMap`, `RMapCache`, `RLocalCachedMap`, `RSet`, `RSetCache` and `RBucket` objects can participate in the transaction.

The transaction isolation level is `READ_COMMITTED`.

In a Valkey or Redis cluster, use `{}` hash-tag braces to keep the keys on the same slot, otherwise the server throws a CROSSSLOT error:

```java
RMap<String, String> map = transaction.getMap("myMap{user:1}");
map.put("1", "2");
String value = map.get("3");
RSet<String> set = transaction.getSet("mySet{user:1}");
set.add(value);
```

Code example:

```java
// transaction obtained from a JTA-compatible transaction manager
Transaction globalTransaction = transactionManager.getTransaction();

RXAResource xaResource = redisson.getXAResource();
globalTransaction.enlistResource(xaResource);

RTransaction transaction = xaResource.getTransaction();
RBucket<String> bucket = transaction.getBucket("myBucket");
bucket.set("simple");
RMap<String, String> map = transaction.getMap("myMap");
map.put("myKey", "myValue");

transactionManager.commit();
```

## Spring Transaction Manager

For Spring Transaction Manager usage, see the [Spring Transaction Manager](integration-with-spring.md#spring-transaction-manager) article.
