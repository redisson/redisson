## License

All contributions are subject to the [Developer Certificate of Origin(DCO)](https://developercertificate.org/). Text of DCO is also included in [DCO.txt](https://github.com/redisson/redisson/blob/master/DCO.txt) file.

All contributions to Redisson are licensed under the
[Apache License 2.0](https://github.com/redisson/redisson/blob/master/header.txt).

## Build Prerequisites
Have at least a local copy of built redis, for more information see [tutorial](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-redis).

Note that redis shouldn't be running - the build will start instances as needed.

## Running the tests

``` bash
export REDIS_BIN=<path to redis binaries>

# And finally running the build
mvn -DargLine="-Xmx2g -DredisBinary=$REDIS_BIN/redis-server" -Punit-test clean test -e
```
