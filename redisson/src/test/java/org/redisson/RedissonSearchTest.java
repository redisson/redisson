package org.redisson;

import com.fasterxml.jackson.core.type.TypeReference;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.search.SpellcheckOptions;
import org.redisson.api.search.aggregate.*;
import org.redisson.api.search.index.*;
import org.redisson.api.search.query.*;
import org.redisson.api.search.query.hybrid.Combine;
import org.redisson.api.search.query.hybrid.HybridQueryArgs;
import org.redisson.api.search.query.hybrid.HybridSearchResult;
import org.redisson.api.search.query.hybrid.VectorSimilarity;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.JacksonCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSearchTest extends RedisDockerTest {
//public class RedissonSearchTest extends DockerRedisStackTest {

    public static class SimpleObject {

        private String name;

        public SimpleObject(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SimpleObject that = (SimpleObject) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
    
    @Test
    public void testSearchWithParam() {
        RJsonBucket<String> b = redisson.getJsonBucket("doc:1", StringCodec.INSTANCE);
        b.set("[{\"arr\": [1, 2, 3]}, {\"val\": \"hello\"}, {\"val\": \"world\"}]");
        
        RSearch s = redisson.getSearch(StringCodec.INSTANCE);
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                s.search("idx", "*", QueryOptions.defaults()
                        .returnAttributes(new ReturnAttribute("arr"),
                                new ReturnAttribute("val"))
                        .params(Collections.singletonMap("12", "323"))));
    }

    @Test
    public void testSearchWithParam2() {
        RJsonBucket<String> b = redisson.getJsonBucket("shape:1", StringCodec.INSTANCE);
        b.set("{\"name\": \"Green Square\", \"geom\": \"POLYGON ((1 1, 1 3, 3 3, 3 1, 1 1))\"}");
        RJsonBucket<String> b2 = redisson.getJsonBucket("shape:2", StringCodec.INSTANCE);
        b2.set("{\"name\": \"Red Rectangle\", \"geom\": \"POLYGON ((2 2.5, 2 3.5, 3.5 3.5, 3.5 2.5, 2 2.5))\"}");
        RJsonBucket<String> b3 = redisson.getJsonBucket("shape:3", StringCodec.INSTANCE);
        b3.set("{\"name\": \"Blue Triangle\", \"geom\": \"POLYGON ((3.5 1, 3.75 2, 4 1, 3.5 1))\"}");
        RJsonBucket<String> b4 = redisson.getJsonBucket("shape:4", StringCodec.INSTANCE);
        b4.set("{\"name\": \"Purple Point\", \"geom\": \"POINT (2 2)\"}");

        RSearch s = redisson.getSearch(StringCodec.INSTANCE);
        assertThat(s.getIndexes()).isEmpty();

        s.createIndex("geomidx", IndexOptions.defaults()
                        .on(IndexType.JSON)
                        .prefix("shape:"),
                FieldIndex.text("$.name").as("name"),
                FieldIndex.geoShape("$.geom").as("geom").coordinateSystems(GeoShapeIndex.CoordinateSystems.FLAT));

        SearchResult r = s.search("geomidx", "(-@name:(Green Square) @geom:[WITHIN $qshape])", QueryOptions.defaults()
                .params(Collections.singletonMap("qshape", "POLYGON ((1 1, 1 3, 3 3, 3 1, 1 1))"))
                .returnAttributes(new ReturnAttribute("name"))
                .dialect(2));
        assertThat(r.getTotal()).isEqualTo(1);
    }
    
    @Test
    public void testSearchNoContent() {
        RMap<String, SimpleObject> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m.put("t1", new SimpleObject("name1"));
        m.put("t2", new SimpleObject("name2"));
        
        RMap<String, SimpleObject> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m2.put("t1", new SimpleObject("name3"));
        m2.put("t2", new SimpleObject("name4"));
        
        RSearch s = redisson.getSearch();
        assertThat(s.getIndexes()).isEmpty();
        
        s.createIndex("idx:1", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));
        
        s.search("idx:1", "*", QueryOptions.defaults().noContent(true));
    }
    
    @Test
    public void testMapAggregateWithCursor() {
        RMap<String, Object> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m.put("t1", new SimpleObject("name1"));
        m.put("t2", new SimpleObject("name2"));
        RMap<String, Object> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m2.put("t1", new SimpleObject("name3"));
        m2.put("t2", new SimpleObject("name4"));

        RSearch s = redisson.getSearch();
        s.createIndex("idx", IndexOptions.defaults()
                                    .on(IndexType.HASH)
                                    .stopwords(Collections.emptyList())
                                    .prefix(Arrays.asList("doc:")),
                                    FieldIndex.text("t1"),
                                    FieldIndex.text("t2"));

        AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                                        .withCursor()
                                                                                        .load("t1", "t2"));
        assertThat(r.getTotal()).isEqualTo(1);
        assertThat(r.getCursorId()).isEqualTo(0);
        assertThat(new HashSet<>(r.getAttributes())).isEqualTo(new HashSet<>(Arrays.asList(m2.readAllMap(), m.readAllMap())));

        AggregationResult r3 = s.aggregate("idx", "*", AggregationOptions.defaults()
                .withCursor(1).load("t1", "t2"));

        assertThat(r3.getTotal()).isEqualTo(1);
        assertThat(r3.getCursorId()).isPositive();
        assertThat(r3.getAttributes()).hasSize(1).isSubsetOf(m.readAllMap(), m2.readAllMap());

        AggregationResult r2 = s.readCursor("idx", r3.getCursorId());
        assertThat(r2.getTotal()).isEqualTo(1);
        assertThat(r2.getCursorId()).isPositive();

        assertThat(r3.getAttributes()).isNotEqualTo(r2.getAttributes());
        assertThat(r2.getAttributes()).hasSize(1).isSubsetOf(m.readAllMap(), m2.readAllMap());

    }

    @Test
    public void testIterableAggregate() {
        for (int i = 0; i < 1000; i++) {
            RMap<String, String> m = redisson.getMap("doc:" + i, StringCodec.INSTANCE);
            m.fastPut("t1", "name" + i);
        }

        RSearch s = redisson.getSearch(StringCodec.INSTANCE);
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"));

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(s.info("idx").getIndexing()).isEqualTo(0);
        });

        Iterable<AggregationEntry> iterable = s.aggregate("idx", "*", IterableAggregationOptions.defaults()
                .cursorCount(100).cursorMaxIdle(Duration.ofSeconds(10))
                .groupBy(GroupBy.fieldNames("@t1").reducers(Reducer.count().as("count"))));
        Iterator<AggregationEntry> iterator = iterable.iterator();
        int count = 0;
        while (iterator.hasNext()) {
            count++;
            AggregationEntry cc = iterator.next();
            assertThat(cc.getAttributes().get("count")).isEqualTo("1");
        }
        assertThat(count).isEqualTo(1000);
    }

    @Test
    public void testExpression() {
        RSearch s = redisson.getSearch();
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        AggregationResult aggregate = s.aggregate("idx", "*", AggregationOptions.defaults()
                .load("$.location", "as", "location", "$.*", "as", "$")
                .apply(new Expression("geodistance(@location, 1, 2)", "dist"))
                .limit(0, 1000));
    }

    @Test
    public void testInfo() {
        for (int i = 0; i < 1000; i++) {
            RMap<String, SimpleObject> m = redisson.getMap("doc:" +i, new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
            m.put("t1", new SimpleObject("name1"));
            m.put("t2", new SimpleObject("name2"));
        }


        RSearch s = redisson.getSearch();
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        IndexInfo r = s.info("idx");
        assertThat(r.getName()).isEqualTo("idx");
        assertThat(r.getAttributes()).hasSize(2);
    }

    @Test
    public void testHasIndex() {
        for (int i = 0; i < 1000; i++) {
            RMap<String, SimpleObject> m = redisson.getMap("doc:" +i, new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
            m.put("t1", new SimpleObject("name1"));
            m.put("t2", new SimpleObject("name2"));
        }

        RSearch s = redisson.getSearch();
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        boolean hasIdx = s.hasIndex("idx");
        assertThat(hasIdx).isTrue();

        boolean hasIdxNotExists = s.hasIndex("idx_not_exists");
        assertThat(hasIdxNotExists).isFalse();
    }

    @Test
    public void testSort() {
        RMap<String, SimpleObject> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m.put("t1", new SimpleObject("name1"));
        m.put("t2", new SimpleObject("name2"));
        RMap<String, SimpleObject> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m2.put("t1", new SimpleObject("name3"));
        m2.put("t2", new SimpleObject("name4"));

        RSearch s = redisson.getSearch();
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                            .load("t1", "t2")
                                                                            .sortBy(new SortedField("@t1")));

        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getCursorId()).isEqualTo(-1);
        assertThat(new HashSet<>(r.getAttributes())).isEqualTo(new HashSet<>(Arrays.asList(m2.readAllMap(), m.readAllMap())));
    }

    @Test
    public void testGroupBy() {
        RMap<String, Object> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m.put("t1", new SimpleObject("name1"));
        m.put("t2", new SimpleObject("name2"));
        RMap<String, Object> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m2.put("t1", new SimpleObject("name3"));
        m2.put("t2", new SimpleObject("name4"));

        RSearch s = redisson.getSearch();
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                            .load("t1", "t2")
                                                                            .groupBy(GroupBy.fieldNames("@t1")));

        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getCursorId()).isEqualTo(-1);
        Map<String, Object> mm2 = m2.readAllMap();
        mm2.remove("t2");
        Map<String, Object> mm = m.readAllMap();
        mm.remove("t2");
        assertThat(new HashSet<>(r.getAttributes())).isEqualTo(new HashSet<>(Arrays.asList(mm2, mm)));

        AggregationResult r2 = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                            .load("t1", "t2")
                                                                            .groupBy(GroupBy.fieldNames("@t1")
                                                                                            .reducers(Reducer.count().as("count"),
                                                                                                    Reducer.min("@t1").as("min"))));

        assertThat(r2.getTotal()).isEqualTo(2);
        assertThat(r2.getCursorId()).isEqualTo(-1);
        mm2.put("count", "1");
        mm2.put("min", "inf");
        mm.put("count", "1");
        mm.put("min", "inf");
        assertThat(new HashSet<>(r2.getAttributes())).isEqualTo(new HashSet<>(Arrays.asList(mm2, mm)));
    }

    @Test
    public void testListIndexes() {
        RMap<String, SimpleObject> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m.put("t1", new SimpleObject("name1"));
        m.put("t2", new SimpleObject("name2"));
        RMap<String, SimpleObject> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m2.put("t1", new SimpleObject("name3"));
        m2.put("t2", new SimpleObject("name4"));

        RSearch s = redisson.getSearch();
        assertThat(s.getIndexes()).isEmpty();

        s.createIndex("idx:1", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        s.createIndex("idx:2", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        assertThat(s.getIndexes()).containsExactlyInAnyOrder("idx:1", "idx:2");
    }

    @Test
    public void testMapAggregate() {
        RMap<String, SimpleObject> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m.put("t1", new SimpleObject("name1"));
        m.put("t2", new SimpleObject("name2"));
        RMap<String, SimpleObject> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m2.put("t1", new SimpleObject("name3"));
        m2.put("t2", new SimpleObject("name4"));

        RSearch s = redisson.getSearch();
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                            .load("t1", "t2"));

        assertThat(r.getTotal()).isEqualTo(1);
        assertThat(r.getCursorId()).isEqualTo(-1);
        assertThat(new HashSet<>(r.getAttributes())).isEqualTo(new HashSet<>(Arrays.asList(m2.readAllMap(), m.readAllMap())));
    }

    @Test
    public void testJSONAggregate() {
        RJsonBucket<String> b = redisson.getJsonBucket("doc:1", StringCodec.INSTANCE);
        b.set("[{\"arr\": [1, 2, 3]}, {\"val\": \"hello\"}, {\"val\": \"world\"}]");

        RSearch s = redisson.getSearch(StringCodec.INSTANCE);
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.JSON)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.numeric("$..arr").as("arr"),
                FieldIndex.text("$..val").as("val"));

        AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
                .load("arr", "val"));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("arr", "[1,2,3]");
        map.put("val", "hello");

        assertThat(r.getTotal()).isEqualTo(1);
        assertThat(r.getAttributes().get(0)).isEqualTo(map);
    }


    @Test
    public void testMapSearch() {
        RMap<String, SimpleObject> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m.put("t1", new SimpleObject("name1"));
        m.put("t2", new SimpleObject("name2"));
        RMap<String, SimpleObject> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
        m2.put("t1", new SimpleObject("name3"));
        m2.put("t2", new SimpleObject("name4"));

        RSearch s = redisson.getSearch();
        s.createIndex("idx", IndexOptions.defaults()
                                                .on(IndexType.HASH)
                                                .prefix(Arrays.asList("doc:")),
                                            FieldIndex.text("t1"),
                                            FieldIndex.text("t2"));

        SearchResult r = s.search("idx", "*", QueryOptions.defaults()
                                                                .returnAttributes(new ReturnAttribute("t1"),
                                                                        new ReturnAttribute("t2")));

        assertThat(r.getTotal()).isEqualTo(2);
        Set<Map<String, Object>> l = r.getDocuments().stream().map(d -> d.getAttributes()).collect(Collectors.toSet());
        assertThat(l).isEqualTo(new HashSet<>(Arrays.asList(m2.readAllMap(), m.readAllMap())));
    }

    @Test
    public void testMapSearchCluster() {
        withNewCluster((nodes, redisson) -> {
            RMap<String, SimpleObject> m = redisson.getMap("{doc}:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
            m.put("t1", new SimpleObject("name1"));
            m.put("t2", new SimpleObject("name2"));
            RMap<String, SimpleObject> m2 = redisson.getMap("{doc}:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
            m2.put("t1", new SimpleObject("name3"));
            m2.put("t2", new SimpleObject("name4"));

            RSearch s = redisson.getSearch();
            s.createIndex("doc", IndexOptions.defaults()
                            .on(IndexType.HASH)
                            .prefix(Arrays.asList("{doc}:")),
                    FieldIndex.text("t1"),
                    FieldIndex.text("t2"));

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            SearchResult r = s.search("doc", "*", QueryOptions.defaults()
                    .returnAttributes(new ReturnAttribute("t1"),
                            new ReturnAttribute("t2")));

            assertThat(r.getTotal()).isEqualTo(2);
            Set<Map<String, Object>> l = r.getDocuments().stream().map(d -> d.getAttributes()).collect(Collectors.toSet());
            assertThat(l).isEqualTo(new HashSet<>(Arrays.asList(m2.readAllMap(), m.readAllMap())));
        });
    }


    @Test
    public void testJSONSearch() {
        RJsonBucket<String> b = redisson.getJsonBucket("doc:1", StringCodec.INSTANCE);
        b.set("[{\"arr\": [1, 2, 3]}, {\"val\": \"hello\"}, {\"val\": \"world\"}]");

        RSearch s = redisson.getSearch(StringCodec.INSTANCE);
        s.createIndex("idx", IndexOptions.defaults()
                                                .on(IndexType.JSON)
                                                .prefix(Arrays.asList("doc:")),
                                    FieldIndex.numeric("$..arr").as("arr"),
                                    FieldIndex.text("$..val").as("val"));

        SearchResult r = s.search("idx", "*", QueryOptions.defaults()
                                                                    .returnAttributes(new ReturnAttribute("arr"),
                                                                            new ReturnAttribute("val")));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("arr", "[1,2,3]");
        map.put("val", "hello");
        assertThat(r.getTotal()).isEqualTo(1);
        assertThat(r.getDocuments())
                        .containsExactly(new Document("doc:1", map));

        SearchResult r2 = s.search("idx", "*", QueryOptions.defaults()
                .returnAttributes(new ReturnAttribute("arr"),
                        new ReturnAttribute("val"))
                        .params(Collections.singletonMap("12", "323"))
                .dialect(3));

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("arr", "[[1,2,3]]");
        map2.put("val", "[\"hello\",\"world\"]");
        assertThat(r2.getTotal()).isEqualTo(1);
        assertThat(r2.getDocuments())
                .containsExactly(new Document("doc:1", map2));

    }

    @Test
    public void testSpellcheck() {
        RSearch s = redisson.getSearch();

        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        assertThat(s.addDict("name", "hockey", "stik")).isEqualTo(2);

        List<String> tt = s.dumpDict("name");
        assertThat(tt).containsExactly("hockey", "stik");

        Map<String, Map<String, Double>> res = s.spellcheck("idx", "Hocke sti", SpellcheckOptions.defaults()
                                                                                                            .includedTerms("name"));
        assertThat(res.get("hocke")).containsExactlyEntriesOf(Collections.singletonMap("hockey", (double) 0));
        assertThat(res.get("sti")).containsExactlyEntriesOf(Collections.singletonMap("stik", (double) 0));

        Map<String, Map<String, Double>> emptyRes = s.spellcheck("idx", "Hocke sti", SpellcheckOptions.defaults());
        assertThat(emptyRes.get("hocke")).isEmpty();
        assertThat(emptyRes.get("sti")).isEmpty();
    }

    public static class TestClass {
        private List<Float> vector;
        private String content;

        public TestClass(List<Float> vector, String content) {
            this.vector = vector;
            this.content = content;
        }

        public List<Float> getVector() {
            return vector;
        }

        public String getContent() {
            return content;
        }
    }

    @Test
    public void testVector() {
        RJsonBucket<List<Float>> b = redisson.getJsonBucket("doc:1", new JacksonCodec<>(new TypeReference<List<Float>>() {}));
        List<Float> vector = Arrays.asList(1F, 2F, 3F, 4F);
        b.set(vector);

        RSearch s = redisson.getSearch(StringCodec.INSTANCE);
        s.createIndex("text_index", IndexOptions.defaults()
                        .on(IndexType.JSON)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.flatVector("$.vector")
                        .as("vector")
                        .type(VectorTypeParam.Type.FLOAT32)
                        .dim(vector.size())
                        .distance(VectorDistParam.DistanceMetric.COSINE),
                FieldIndex.hnswVector("$.vector")
                        .as("vector2")
                        .type(VectorTypeParam.Type.FLOAT32)
                        .dim(vector.size())
                        .distance(VectorDistParam.DistanceMetric.COSINE),
                FieldIndex.text("$.content").as("content"));

        SearchResult r = s.search("text_index", "*", QueryOptions.defaults()
                                                                            .returnAttributes(new ReturnAttribute("vector", "vector11"),
                                                                                                new ReturnAttribute("vector2", "vector22")));
        assertThat(r.getTotal()).isEqualTo(1);
    }

    @Test
    public void testVector2() {
        RJsonBucket<TestClass> b = redisson.getJsonBucket("doc:1", new JacksonCodec<>(new TypeReference<TestClass>() {
        }));
        List<Float> vector = Arrays.asList(1F, 2F, 3F, 4F);
        TestClass test = new TestClass(vector, "test_content");
        b.set(test);

        RSearch s = redisson.getSearch(StringCodec.INSTANCE);
        s.createIndex("text_index", IndexOptions.defaults()
                        .on(IndexType.JSON)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.svsVamanaVector("$.vector")
                        .as("vector1")
                        .type(VectorTypeParam.Type.FLOAT32)
                        .dim(vector.size())
                        .distance(VectorDistParam.DistanceMetric.COSINE));

        SearchResult r = s.search("text_index", "*", QueryOptions.defaults()
                .returnAttributes(new ReturnAttribute("vector1", "vector11")
                ));
        assertThat(r.getTotal()).isEqualTo(1);
    }

    @Test
    public void testFieldTag() {
        IndexOptions indexOptions = IndexOptions.defaults()
                .on(IndexType.JSON)
                .prefix(Arrays.asList("items"));

        FieldIndex[] fields = new FieldIndex[]{
                FieldIndex.tag("$.name")
                        .caseSensitive()
                        .withSuffixTrie()
                        .noIndex()
                        .separator("a")
                        .sortMode(SortMode.NORMALIZED)
                        .as("name")
        };
        RSearch s = redisson.getSearch();
        s.createIndex("itemIndex", indexOptions, fields);
    }

    @Test
    public void testFieldText() {
        IndexOptions indexOptions = IndexOptions.defaults()
                .on(IndexType.JSON)
                .prefix(Arrays.asList("items"));

        FieldIndex[] fields = new FieldIndex[]{
                FieldIndex.text("$.name")
                        .noStem()
                        .noIndex()
                        .sortMode(SortMode.NORMALIZED)
                        .as("name")
        };
        RSearch s = redisson.getSearch();
        s.createIndex("itemIndex", indexOptions, fields);
    }

    @Test
    public void testSynonyms() {
        RSearch s = redisson.getSearch();

        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"),
                FieldIndex.text("t2"));

        s.updateSynonyms("idx", "group1", "d", "dd", "df");
        s.updateSynonyms("idx", "group2", "dd");
        Map<String, List<String>> r = s.dumpSynonyms("idx");

        Map<String, List<String>> m = new HashMap<>();
        m.put("df", Arrays.asList("group1"));
        m.put("d", Arrays.asList("group1"));
        m.put("dd", Arrays.asList("group1", "group2"));
        assertThat(r).isEqualTo(m);
    }

    @Nested
    class HybridSearchTests {

        @Test
        public void testHybridSearchBasic() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("product:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.text("$.description").as("description"),
                    FieldIndex.numeric("$.price").as("price"),
                    FieldIndex.flatVector("$.embedding")
                            .as("embedding")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            RJsonBucket<Map<String, Object>> doc1 = redisson.getJsonBucket("product:1",
                    new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
            Map<String, Object> product1 = new HashMap<>();
            product1.put("name", "laptop");
            product1.put("description", "powerful laptop for developers");
            product1.put("price", 1299.99);
            product1.put("embedding", Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f));
            doc1.set(product1);

            RJsonBucket<Map<String, Object>> doc2 = redisson.getJsonBucket("product:2",
                    new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
            Map<String, Object> product2 = new HashMap<>();
            product2.put("name", "desktop computer");
            product2.put("description", "high performance desktop");
            product2.put("price", 1899.99);
            product2.put("embedding", Arrays.asList(0.2f, 0.3f, 0.4f, 0.5f));
            doc2.set(product2);

            byte[] queryVector = floatsToBytes(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

            HybridSearchResult result = s.hybridSearch("hybrid-idx",
                    HybridQueryArgs.query("laptop")
                            .vectorSimilarity(VectorSimilarity.of("@embedding", "$vec")
                                                                .nearestNeighbors(10))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 10));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "product:1", "__score", "0.0327868852459"),
                    Map.of("__key", "product:2", "__score", "0.0161290322581"));

            HybridSearchResult result2 = s.hybridSearch("hybrid-idx",
                    HybridQueryArgs.query("laptop")
                            .vectorSimilarity(VectorSimilarity.of("@embedding", "$vec")
                                    .nearestNeighbors(10).scoreAlias("test").filter("@test:"))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 10));

            assertThat(result2.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "product:1", "__score", "0.0327868852459", "test", "1"),
                    Map.of("__key", "product:2", "__score", "0.0161290322581", "test", "0.996903955936"));

            HybridSearchResult result3 = s.hybridSearch("hybrid-idx",
                    HybridQueryArgs.query("laptop")
                            .vectorSimilarity(VectorSimilarity.of("@embedding", "$vec")
                                    .range(10).scoreAlias("test").filter("@test:"))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 10));

            assertThat(result3.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "product:1", "__score", "0.0327868852459", "test", "1"),
                    Map.of("__key", "product:2", "__score", "0.0161290322581", "test", "0.996903955936"));

        }

        @Test
        public void testHybridSearchWithSimilarity() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-knn-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("item:")),
                    FieldIndex.text("$.title").as("title"),
                    FieldIndex.flatVector("$.vector")
                            .as("vector")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.L2));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("item:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("title", "Document " + i);
                item.put("vector", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

            HybridSearchResult result = s.hybridSearch("hybrid-knn-idx",
                    HybridQueryArgs.query("document")
                            .vectorSimilarity(VectorSimilarity.of("@vector", "$vec").scoreAlias("asd"))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 3));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "item:1", "__score", "0.0327868852459", "asd", "1"),
                    Map.of("__key", "item:2", "__score", "0.0322580645161", "asd", "0.769230762177"),
                    Map.of("__key", "item:3", "__score", "0.031746031746", "asd", "0.454545420063"));
        }

        @Test
        public void testHybridSearchWithKnn() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-knn-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("item:")),
                    FieldIndex.text("$.title").as("title"),
                    FieldIndex.flatVector("$.vector")
                            .as("vector")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.L2));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("item:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("title", "Document " + i);
                item.put("vector", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

            HybridSearchResult result = s.hybridSearch("hybrid-knn-idx",
                    HybridQueryArgs.query("document")
                            .vectorSimilarity(VectorSimilarity.of("@vector", "$vec")
                                                                .nearestNeighbors(3))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 3));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "item:1", "__score", "0.0327868852459"),
                    Map.of("__key", "item:2", "__score", "0.0322580645161"),
                    Map.of("__key", "item:3", "__score", "0.031746031746"));
        }

        @Test
        public void testHybridSearchWithKnnAndEfRuntime() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-ef-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("doc:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.hnswVector("$.embedding")
                            .as("embedding")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE)
                            .m(16)
                            .efConstruction(200));

            for (int i = 1; i <= 10; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("doc:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("name", "Item " + i);
                item.put("embedding", Arrays.asList(0.05f * i, 0.1f * i, 0.15f * i, 0.2f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.25f, 0.5f, 0.75f, 1.0f});

            HybridSearchResult result = s.hybridSearch("hybrid-ef-idx",
                    HybridQueryArgs.query("item")
                            .vectorSimilarity(VectorSimilarity.of("@embedding", "$vec").
                                                                nearestNeighbors(5)
                                    .efRuntime(100))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "doc:1", "__score", "0.032522474881"),
                    Map.of("__key", "doc:2", "__score", "0.0320020481311"),
                    Map.of("__key", "doc:3", "__score", "0.031498015873"),
                    Map.of("__key", "doc:4", "__score", "0.0310096153846"),
                    Map.of("__key", "doc:9", "__score", "0.0308861962461"));
        }

        @Test
        public void testHybridSearchWithRange() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-range-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("vec:")),
                    FieldIndex.text("$.label").as("label"),
                    FieldIndex.flatVector("$.data")
                            .as("data")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.L2));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("vec:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("label", "vector " + i);
                item.put("data", Arrays.asList(0.1f * i, 0.1f * i, 0.1f * i, 0.1f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.1f, 0.1f, 0.1f, 0.1f});

            HybridSearchResult result = s.hybridSearch("hybrid-range-idx",
                    HybridQueryArgs.query("vector")
                            .vectorSimilarity(VectorSimilarity.of("@data", "$vec")
                                                                .range(1.0))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 10));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "vec:1", "__score", "0.0327868852459"),
                    Map.of("__key", "vec:2", "__score", "0.0322580645161"),
                    Map.of("__key", "vec:3", "__score", "0.031746031746"),
                    Map.of("__key", "vec:4", "__score", "0.03125"),
                    Map.of("__key", "vec:5", "__score", "0.0307692307692"));
        }

        @Test
        public void testHybridSearchWithRrfCombine() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-rrf-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("rrf:")),
                    FieldIndex.text("$.title").as("title"),
                    FieldIndex.flatVector("$.embedding")
                            .as("embedding")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("rrf:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("title", "Article " + i + " about technology");
                item.put("embedding", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.5f, 1.0f, 1.5f, 2.0f});

            HybridSearchResult result = s.hybridSearch("hybrid-rrf-idx",
                    HybridQueryArgs.query("technology")
                            .vectorSimilarity(VectorSimilarity.of("@embedding", "$vec")
                                                                .nearestNeighbors(10))
                            .params(Map.of("vec", queryVector))
                            .combine(Combine.reciprocalRankFusion()
                                    .window(20)
                                    .constant(60))
                            .limit(0, 10));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "rrf:1", "__score", "0.0327868852459"),
                    Map.of("__key", "rrf:2", "__score", "0.0322580645161"),
                    Map.of("__key", "rrf:3", "__score", "0.031746031746"),
                    Map.of("__key", "rrf:4", "__score", "0.03125"),
                    Map.of("__key", "rrf:5", "__score", "0.0307692307692"));
        }

        @Test
        public void testHybridSearchWithLinearCombine() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-linear-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("lin:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.flatVector("$.vector")
                            .as("vector")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("lin:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("name", "Product " + i);
                item.put("vector", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.2f, 0.4f, 0.6f, 0.8f});

            HybridSearchResult result = s.hybridSearch("hybrid-linear-idx",
                    HybridQueryArgs.query("product")
                            .vectorSimilarity(VectorSimilarity.of("@vector", "$vec")
                                                                .nearestNeighbors(5))
                            .params(Map.of("vec", queryVector))
                            .combine(Combine.linear()
                                    .alpha(0.7)
                                    .beta(0.3))
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "lin:1", "__score", "0.360907984754"),
                    Map.of("__key", "lin:2", "__score", "0.360907984754"),
                    Map.of("__key", "lin:3", "__score", "0.360907984754"),
                    Map.of("__key", "lin:4", "__score", "0.360907984754"),
                    Map.of("__key", "lin:5", "__score", "0.360907975814"));
        }

        @Test
        public void testHybridSearchWithScoreAliases() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-alias-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("alias:")),
                    FieldIndex.text("$.content").as("content"),
                    FieldIndex.flatVector("$.emb")
                            .as("emb")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("alias:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("content", "sample text " + i);
                item.put("emb", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.2f, 0.4f, 0.6f, 0.8f});

            HybridSearchResult result = s.hybridSearch("hybrid-alias-idx",
                    HybridQueryArgs.query("sample")
                            .scoreAlias("text_score")
                            .vectorSimilarity(VectorSimilarity.of("@emb", "$vec")
                                                                .nearestNeighbors(5))
                            .params(Map.of("vec", queryVector))
                            .combine(Combine.reciprocalRankFusion()
                                    .window(10)
                                    .constant(21))
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "alias:1", "__score", "0.0909090909091", "text_score", "0.174022813584"),
                    Map.of("__key", "alias:2", "__score", "0.0869565217391", "text_score", "0.174022813584"),
                    Map.of("__key", "alias:3", "__score", "0.0833333333333", "text_score", "0.174022813584"),
                    Map.of("__key", "alias:4", "__score", "0.08", "text_score", "0.174022813584"),
                    Map.of("__key", "alias:5", "__score", "0.0769230769231", "text_score", "0.174022813584"));
        }

        @Test
        public void testHybridSearchWithPreFilter() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-filter-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("filt:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.numeric("$.price").as("price"),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 10; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("filt:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("name", "product " + i);
                item.put("price", 100.0 * i);
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.5f, 1.0f, 1.5f, 2.0f});

            HybridSearchResult result = s.hybridSearch("hybrid-filter-idx",
                    HybridQueryArgs.query("product")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(10))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "filt:1", "__score", "0.032522474881"),
                    Map.of("__key", "filt:2", "__score", "0.0320020481311"),
                    Map.of("__key", "filt:3", "__score", "0.031498015873"),
                    Map.of("__key", "filt:4", "__score", "0.0310096153846"),
                    Map.of("__key", "filt:9", "__score", "0.0308861962461"));
        }

        @Test
        public void testHybridSearchWithSortBy() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-sort-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("sort:")),
                    FieldIndex.text("$.title").as("title"),
                    FieldIndex.numeric("$.rating").as("rating").sortMode(SortMode.NORMALIZED),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("sort:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("title", "Item " + i);
                item.put("rating", (double) (6 - i));
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.3f, 0.6f, 0.9f, 1.2f});

            HybridSearchResult result = s.hybridSearch("hybrid-sort-idx",
                    HybridQueryArgs.query("item")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(5))
                            .params(Map.of("vec", queryVector))
                            .sortBy("@rating", SortOrder.DESC)
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "sort:5", "__score", "0.0307692307692"),
                    Map.of("__key", "sort:4", "__score", "0.03125"),
                    Map.of("__key", "sort:3", "__score", "0.031746031746"),
                    Map.of("__key", "sort:2", "__score", "0.0322580645161"),
                    Map.of("__key", "sort:1", "__score", "0.0327868852459"));
        }

        @Test
        public void testHybridSearchWithLoad() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-load-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("load:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.text("$.category").as("category"),
                    FieldIndex.numeric("$.price").as("price"),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("load:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("name", "Product " + i);
                item.put("category", "Electronics");
                item.put("price", 99.99 * i);
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.2f, 0.4f, 0.6f, 0.8f});

            HybridSearchResult result = s.hybridSearch("hybrid-load-idx",
                    HybridQueryArgs.query("product")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(5))
                            .params(Map.of("vec", queryVector))
                            .load("@name", "@category", "@price")
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("category", "Electronics", "name", "Product 1", "price", "99.99"),
                    Map.of("category", "Electronics", "name", "Product 2", "price", "199.98"),
                    Map.of("category", "Electronics", "name", "Product 3", "price", "299.97"),
                    Map.of("category", "Electronics", "name", "Product 4", "price", "399.96"),
                    Map.of("category", "Electronics", "name", "Product 5", "price", "499.95"));
        }

        @Test
        public void testHybridSearchWithGroupBy() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-group-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("grp:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.tag("$.category").as("category"),
                    FieldIndex.numeric("$.price").as("price"),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            String[] categories = {"electronics", "clothing", "books"};
            for (int i = 1; i <= 9; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("grp:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("name", "Product " + i);
                item.put("category", categories[(i - 1) % 3]);
                item.put("price", 100.0 * i);
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.5f, 1.0f, 1.5f, 2.0f});

            HybridSearchResult result = s.hybridSearch("hybrid-group-idx",
                    HybridQueryArgs.query("product")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(10))
                            .params(Map.of("vec", queryVector))
                            .groupBy(org.redisson.api.search.GroupBy.fieldNames("@category")
                                    .reducers(org.redisson.api.search.Reducer.count().as("count"),
                                            org.redisson.api.search.Reducer.avg("@price").as("avg_price")))
                            .limit(0, 10));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("avg_price", "500", "category", "clothing", "count", "3"),
                    Map.of("avg_price", "400", "category", "electronics", "count", "3"),
                    Map.of("avg_price", "600", "category", "books", "count", "3"));
        }

        @Test
        public void testHybridSearchWithApply() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-apply-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("apply:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.numeric("$.price").as("price"),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("apply:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("name", "item " + i);
                item.put("price", 100.0 * i);
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.25f, 0.5f, 0.75f, 1.0f});

            // With APPLY transformation
            HybridSearchResult result = s.hybridSearch("hybrid-apply-idx",
                    HybridQueryArgs.query("item")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(5))
                            .params(Map.of("vec", queryVector))
                            .load("@price")
                            .apply(new org.redisson.api.search.Expression("@price * 0.9", "discounted_price"))
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("discounted_price", "90", "price", "100"),
                    Map.of("discounted_price", "180", "price", "200"),
                    Map.of("discounted_price", "270", "price", "300"),
                    Map.of("discounted_price", "360", "price", "400"),
                    Map.of("discounted_price", "450", "price","500"));
        }

        @Test
        public void testHybridSearchWithTimeout() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-timeout-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("timeout:")),
                    FieldIndex.text("$.text").as("text"),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 3; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("timeout:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("text", "content " + i);
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.15f, 0.3f, 0.45f, 0.6f});

            // With TIMEOUT
            HybridSearchResult result = s.hybridSearch("hybrid-timeout-idx",
                    HybridQueryArgs.query("content")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(3))
                            .params(Map.of("vec", queryVector))
                            .timeout(Duration.ofSeconds(5))
                            .limit(0, 3));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "timeout:1", "__score", "0.0327868852459"),
                    Map.of("__key", "timeout:2", "__score", "0.0322580645161"),
                    Map.of("__key", "timeout:3", "__score", "0.031746031746"));
        }

        @Test
        public void testHybridSearchWithScorer() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-scorer-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("scorer:")),
                    FieldIndex.text("$.title").as("title"),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 5; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("scorer:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("title", "Document title " + i);
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.25f, 0.5f, 0.75f, 1.0f});

            // With custom SCORER
            HybridSearchResult result = s.hybridSearch("hybrid-scorer-idx",
                    HybridQueryArgs.query("document")
                            .scorer("BM25")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(5))
                            .params(Map.of("vec", queryVector))
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "scorer:1", "__score", "0.0327868852459"),
                    Map.of("__key", "scorer:2", "__score", "0.0322580645161"),
                    Map.of("__key", "scorer:3", "__score", "0.031746031746"),
                    Map.of("__key", "scorer:4", "__score", "0.03125"),
                    Map.of("__key", "scorer:5", "__score", "0.0307692307692"));
        }

        @Test
        public void testHybridSearchWithNoSort() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-nosort-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("nosort:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 3; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("nosort:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("name", "item " + i);
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.15f, 0.3f, 0.45f, 0.6f});

            // With NOSORT
            HybridSearchResult result = s.hybridSearch("hybrid-nosort-idx",
                    HybridQueryArgs.query("item")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(3))
                            .params(Map.of("vec", queryVector))
                            .noSort()
                            .limit(0, 3));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "nosort:1", "__score", "0.0327868852459"),
                    Map.of("__key", "nosort:2", "__score", "0.0322580645161"),
                    Map.of("__key", "nosort:3", "__score", "0.031746031746"));
        }

        @Test
        public void testHybridSearchWithPostFilter() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-postfilter-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("pf:")),
                    FieldIndex.text("$.name").as("name"),
                    FieldIndex.numeric("$.score").as("score"),
                    FieldIndex.flatVector("$.vec")
                            .as("vec")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            for (int i = 1; i <= 10; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("pf:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("name", "item " + i);
                item.put("score", (double) (i * 10));
                item.put("vec", Arrays.asList(0.1f * i, 0.2f * i, 0.3f * i, 0.4f * i));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.5f, 1.0f, 1.5f, 2.0f});

            // With post-filter after COMBINE
            HybridSearchResult result = s.hybridSearch("hybrid-postfilter-idx",
                    HybridQueryArgs.query("item")
                            .vectorSimilarity(VectorSimilarity.of("@vec", "$vec")
                                                                .nearestNeighbors(10))
                            .params(Map.of("vec", queryVector))
                            .combine(Combine.reciprocalRankFusion().window(10))
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("__key", "pf:1", "__score", "0.032522474881"),
                    Map.of("__key", "pf:2", "__score", "0.0320020481311"),
                    Map.of("__key", "pf:3", "__score", "0.031498015873"),
                    Map.of("__key", "pf:4", "__score", "0.0310096153846"),
                    Map.of("__key", "pf:9", "__score", "0.0308861962461"));
        }

        @Test
        public void testHybridSearchFullOptions() {
            RSearch s = redisson.getSearch(StringCodec.INSTANCE);

            s.createIndex("hybrid-full-idx", IndexOptions.defaults()
                            .on(IndexType.JSON)
                            .prefix(Arrays.asList("full:")),
                    FieldIndex.text("$.title").as("title"),
                    FieldIndex.text("$.description").as("description"),
                    FieldIndex.numeric("$.price").as("price").sortMode(SortMode.NORMALIZED),
                    FieldIndex.numeric("$.rating").as("rating").sortMode(SortMode.NORMALIZED),
                    FieldIndex.tag("$.category").as("category"),
                    FieldIndex.flatVector("$.embedding")
                            .as("embedding")
                            .type(VectorTypeParam.Type.FLOAT32)
                            .dim(4)
                            .distance(VectorDistParam.DistanceMetric.COSINE));

            String[] categories = {"electronics", "clothing", "books"};
            for (int i = 1; i <= 15; i++) {
                RJsonBucket<Map<String, Object>> doc = redisson.getJsonBucket("full:" + i,
                        new JacksonCodec<>(new TypeReference<Map<String, Object>>() {}));
                Map<String, Object> item = new HashMap<>();
                item.put("title", "Product " + i);
                item.put("description", "High quality product number " + i);
                item.put("price", 50.0 + (10.0 * i));
                item.put("rating", 3.0 + (i % 3));
                item.put("category", categories[i % 3]);
                item.put("embedding", Arrays.asList(
                        0.1f * (i % 5 + 1),
                        0.2f * (i % 5 + 1),
                        0.3f * (i % 5 + 1),
                        0.4f * (i % 5 + 1)));
                doc.set(item);
            }

            byte[] queryVector = floatsToBytes(new float[]{0.3f, 0.6f, 0.9f, 1.2f});

            // Full options test
            HybridSearchResult result = s.hybridSearch("hybrid-full-idx",
                    HybridQueryArgs.query("@category:{electronics}")
                            .scorer("BM25")
                            .scoreAlias("text_score")
                            .vectorSimilarity(VectorSimilarity.of("@embedding", "$vec")
                                    .scoreAlias("vector_score")
                            )
                            .params(Map.of("vec", queryVector))
                            .sortBy("@rating", SortOrder.DESC)
                            .combine(Combine.reciprocalRankFusion()
                                    .window(30)
                                    .constant(60))
                            .load("@title", "@price", "@rating", "@category")
                            .apply(new org.redisson.api.search.Expression("@price * 0.85", "sale_price"))
                            .timeout(Duration.ofSeconds(10))
                            .limit(0, 5));

            assertThat(result.getResults()).containsExactlyInAnyOrder(
                    Map.of("category","books", "price","160", "rating","5", "sale_price","136", "title","Product 11", "vector_score","0.999999970198"),
                    Map.of("category","books", "price","130", "rating","5", "sale_price","110.5", "title","Product 8", "vector_score","0.999999970198"),
                    Map.of("category","books", "price","100", "rating","5", "sale_price","85", "title","Product 5", "vector_score","0.999999970198"),
                    Map.of("category","books", "price","70", "rating","5", "sale_price","59.5", "title","Product 2", "vector_score","0.999999970198"),
                    Map.of("category","clothing", "price","150", "rating","4", "sale_price","127.5", "title","Product 10", "vector_score","0.999999970198"));
        }

        private byte[] floatsToBytes(float[] floats) {
            ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (float f : floats) {
                buffer.putFloat(f);
            }
            return buffer.array();
        }

    }
}
