## Build Prerequisites ##
Have at least a local copy of built redis, for more information see [tutorial](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-redis).

Note that redis shouldn't be running - the build will start instances as needed.


## Choosing the building branch
Prior to the build, you may need to change branch depending on which version of Redisson you want to build. Currently master branch is for version 2.x whereas 3.0.0 branch is for 3.x branch. Version 2.x requies Java SDK 6 and above and version 3.x requires Java SDK 8 and above to build.


## Running the tests ##

``` bash
export REDIS_BIN=<path to redis binaries>

# And finally running the build
mvn -DargLine="-Xmx2g -DredisBinary=$REDIS_BIN/redis-server" -Punit-test clean test -e -X
```
