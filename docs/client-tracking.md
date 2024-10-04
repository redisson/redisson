Client tracking listener is invoked when an invalidation message is received if the data previously requested has been changed. Next listener invocation  will be made only if a new data request has been made and another change has occurred since then. 

Available for [RBucket](data-and-services/objects.md/#object-holder), [RStream](data-and-services/objects.md/#stream), [RSet](data-and-services/objects.md/#set), [RMap](data-and-services/objects.md/#map), [RScoredSortedSet](data-and-services/collections.md/#scoredsortedset), [RList](data-and-services/collections.md/#list), [RQueue](data-and-services/collections.md/#queue), [RDeque](data-and-services/collections.md/#deque), [RBlockingQueue](data-and-services/collections.md/#blocking-queue), [RBlockingDeque](data-and-services/collections.md/#blocking-deque), [RDelayedQueue](data-and-services/collections.md/#delayed-queue), [RRingBuffer](data-and-services/collections.md/#ring-buffer) objects.

Requires [protocol](configuration.md) setting value set to `RESP3`.

Code usage example.
```java
RBucket<String> b = redisson.getBucket("test");
int listenerId = b.addListener(new TrackingListener() {
     @Override
     public void onChange(String name) {
         // ...
     }
});

// data requested and change is now tracked
b.get();

// ...

// stop tracking
b.removeListener(listenerId);
```

**Flush listener**

Flush listener is executed on flushall/flushdb commands execution.

```java
redisson.getKeys().addListener(new FlushListener() {
    @Override
    public void onFlush(InetSocketAddress address) {
       // ...
    }
});
```