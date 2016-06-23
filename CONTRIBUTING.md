## Build Prerequisites ##
Have at least a local copy of built redis, for more information see [tutorial](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-redis).

## Running the tests ##

```
bash
export REDIS_BIN=<path to redis binaries>
export REDIS_VERSION="$(redis-cli INFO SERVER | sed -n 2p)"

# And finally running the build
mvn -DargLine="-Xmx2g -DredisBinary=$REDIS_BIN/redis-server -DtravisEnv=true" -Punit-test clean test -e -X
```