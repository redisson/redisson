Below is the libraries used by Redisson:

| Group id | Artifact Id | Version | Dependency |
| ------------- | ------------- | ------------| ------------|
| com.esotericsoftware | kryo | 5.4+| **required** (if Kryo is used as codec)|
| com.esotericsoftware | reflectasm | 1.11+ | **required** (if Kryo is used as codec)|
| com.esotericsoftware | minlog | 1.3+ | **required** (if Kryo is used as codec)|
| org.objenesis | objenesis| 3.3+ | **required** (if Kryo is used as codec)|
| io.netty | netty-common | 4.1+ | **required**|
| io.netty | netty-codec | 4.1+ | **required** |
| io.netty | netty-buffer | 4.1+ | **required** |
| io.netty | netty-transport | 4.1+ | **required** |
| io.netty | netty-handler | 4.1+ | **required** |
| io.netty | netty-resolver | 4.1+ | **required** |
| io.netty | netty-resolver-dns | 4.1+ | **required** |
| com.fasterxml.jackson.dataformat | jackson-core | 2.7+ | **required** |
| com.fasterxml.jackson.dataformat | jackson-databind | 2.7+ | **required** |
| com.fasterxml.jackson.dataformat | jackson-annotations | 2.7+ | **required** |
| com.fasterxml.jackson.dataformat | jackson-dataformat-yaml | 2.7+ | **required**  |
| org.yaml | snakeyaml | 2.0+ | **required**  |
| net.bytebuddy | byte-buddy | 1.6+ | _optional (used by LiveObject service)_ |
| org.jodd | jodd-util | 6.0+ | _optional (used by LiveObject service)_ |
| javax.cache | cache-api | 1.1.1 | _optional (used by JCache implementation)_ |
| io.projectreactor | reactor-core | 3.1+ | _optional (used by RedissonReactiveClient)_ |
| io.reactivex.rxjava3 | rxjava | 3.0+ | _optional (used by RedissonRxClient)_ |

