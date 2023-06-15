package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RJsonBucket;
import org.redisson.api.RMap;
import org.redisson.api.RSearch;
import org.redisson.api.search.SpellcheckOptions;
import org.redisson.api.search.aggregate.AggregationOptions;
import org.redisson.api.search.aggregate.AggregationResult;
import org.redisson.api.search.index.FieldIndex;
import org.redisson.api.search.index.IndexInfo;
import org.redisson.api.search.index.IndexOptions;
import org.redisson.api.search.index.IndexType;
import org.redisson.api.search.query.Document;
import org.redisson.api.search.query.QueryOptions;
import org.redisson.api.search.query.ReturnAttribute;
import org.redisson.api.search.query.SearchResult;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSearchTest extends BaseTest {

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
    public void testMapAggregateWithCursor() {
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
                                                                                        .withCursor()
                                                                                        .load("t1", "t2"));

        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getCursorId()).isEqualTo(0);
        assertThat(new HashSet<>(r.getAttributes())).isEqualTo(new HashSet<>(Arrays.asList(m2.readAllMap(), m.readAllMap())));
    }

    @Test
    public void testInfo() {
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

        IndexInfo r = s.info("idx");
        assertThat(r.getName()).isEqualTo("idx");
        assertThat(r.getAttributes()).hasSize(2);
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

        assertThat(r.getTotal()).isEqualTo(2);
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
