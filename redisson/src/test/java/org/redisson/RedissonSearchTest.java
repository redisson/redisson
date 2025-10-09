package org.redisson;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RJsonBucket;
import org.redisson.api.RMap;
import org.redisson.api.RSearch;
import org.redisson.api.RedissonClient;
import org.redisson.api.search.SpellcheckOptions;
import org.redisson.api.search.aggregate.*;
import org.redisson.api.search.index.*;
import org.redisson.api.search.query.Document;
import org.redisson.api.search.query.QueryOptions;
import org.redisson.api.search.query.ReturnAttribute;
import org.redisson.api.search.query.SearchResult;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.JacksonCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSearchTest extends DockerRedisStackTest {

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
        mm2.put("min", "0");
        mm.put("count", "1");
        mm.put("min", "0");
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
    public void testMapAggregateCursor() {
        for (int i = 0; i < 1000; i++) {
            RMap<String, String> m = redisson.getMap("doc:" + i, StringCodec.INSTANCE);
            m.fastPut("t1", "name"+i);
        }

        RSearch s = redisson.getSearch(StringCodec.INSTANCE);
        s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.HASH)
                        .prefix(Arrays.asList("doc:")),
                FieldIndex.text("t1"));

        AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                                        .withCursor()
                                                                                        .limit(0, 1)
                                                                                        .load("t1", "t2"));

        assertThat(r.getTotal()).isPositive();
        assertThat(r.getCursorId()).isPositive();
        assertThat(r.getAttributes()).hasSizeGreaterThan(0);

        AggregationResult r1 = s.readCursor("idx", r.getCursorId());
        assertThat(r1).isNotNull();
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
                Thread.sleep(300);
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
        GenericContainer<?> redis = RedisDockerTest.createRedisWithVersion("redis:8.2.0");
        redis.start();

        Config config = RedisDockerTest.createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

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


}
