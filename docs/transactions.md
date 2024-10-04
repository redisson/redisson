## Transactions management
`RMap`, `RMapCache`, `RLocalCachedMap`, `RSet`, `RSetCache` and `RBucket` Redisson objects may participant in Transaction with ACID properties. It uses locks for write operations and maintains data modification operations list until `commit()` method is executed. Acquired locks are released after `rollback()` method execution. Implementation throws `org.redisson.transaction.TransactionException` in case of any error during commit/rollback execution.  

Transaction isolation level is: `READ_COMMITTED`. 

Follow Transaction options are available:
```java
TransactionOptions options = TransactionOptions.defaults()
// Synchronization data timeout between Redis or Valkey master participating in transaction and its slaves.
// Default is 5000 milliseconds.
.syncSlavesTimeout(5, TimeUnit.SECONDS)

// Response timeout
// Default is 3000 milliseconds.
.responseTimeout(3, TimeUnit.SECONDS)

// Defines time interval for each attempt to send transaction if it hasn't been sent already.
// Default is 1500 milliseconds.
.retryInterval(2, TimeUnit.SECONDS)

// Defines attempts amount to send transaction if it hasn't been sent already.
// Default is 3 attempts.
.retryAttempts(3)

// If transaction hasn't committed within <code>timeout</code> it will rollback automatically.
// Default is 5000 milliseconds.
.timeout(5, TimeUnit.SECONDS);
```

For execution in Redis or Valkey cluster use {} brackets for collocation data on the same slot otherwise CROSSSLOT error is thrown by Redis.

Code example for Redis or Valkey cluster: 

```java
RMap<String, String> map = transaction.getMap("myMap{user:1}");
map.put("1", "2");
String value = map.get("3");
RSet<String> set = transaction.getSet("mySet{user:1}")
set.add(value);
```

Code example of **Sync / Async** exection:
```java
RedissonClient redisson = Redisson.create(config);
RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());

RMap<String, String> map = transaction.getMap("myMap");
map.put("1", "2");
String value = map.get("3");
RSet<String> set = transaction.getSet("mySet")
set.add(value);

try {
   transaction.commit();
} catch(TransactionException e) {
   transaction.rollback();
}

// or

RFuture<Void> future = transaction.commitAsync();
future.exceptionally(exception -> {
   transaction.rollbackAsync();
});
```

Code example of **Reactive** exection:
```java
RedissonReactiveClient redisson = Redisson.create(config).reactive();
RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults());

RMapReactive<String, String> map = transaction.getMap("myMap");
map.put("1", "2");
Mono<String> mapGet = map.get("3");
RSetReactive<String> set = transaction.getSet("mySet")
set.add(value);

Mono<Void> mono = transaction.commit();
mono.onErrorResume(exception -> {
   return transaction.rollback();
});
```

Code example of **RxJava3** exection:
```java
RedissonRxClient redisson = Redisson.create(config).rxJava();
RTransactionRx transaction = redisson.createTransaction(TransactionOptions.defaults());

RMapRx<String, String> map = transaction.getMap("myMap");
map.put("1", "2");
Maybe<String> mapGet = map.get("3");
RSetRx<String> set = transaction.getSet("mySet")
set.add(value);

Completable res = transaction.commit();
res.onErrorResumeNext(exception -> {
   return transaction.rollback();
});
```

## XA Transactions

_This feature is available only in [Redisson PRO](https://redisson.pro) edition._

Redisson provides [XAResource](https://docs.oracle.com/javaee/7/api/javax/transaction/xa/XAResource.html) implementation to participate in JTA transactions. `RMap`, `RMapCache`, `RLocalCachedMap`, `RSet`, `RSetCache` and `RBucket` Redisson objects may participant in Transaction.

Transaction isolation level is: `READ_COMMITTED`.

For execution in Redis or Valkey cluster use {} brackets for collocation data on the same slot otherwise CROSSSLOT error is thrown by Redis.

Code example for Redis or Valkey cluster: 

```java
RMap<String, String> map = transaction.getMap("myMap{user:1}");
map.put("1", "2");
String value = map.get("3");
RSet<String> set = transaction.getSet("mySet{user:1}")
set.add(value);
```

Code example:
```java
// transaction obtained from JTA compatible transaction manager
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

For Spring Transaction Manager usage please refer to [Spring Transaction Manager](integration-with-spring.md/#spring-transaction-manager) article.
