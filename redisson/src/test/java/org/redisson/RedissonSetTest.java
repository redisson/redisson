package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.SortOrder;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;

public class RedissonSetTest extends BaseTest {

    public static class SimpleBean implements Serializable {

        private Long lng;

        public Long getLng() {
            return lng;
        }

        public void setLng(Long lng) {
            this.lng = lng;
        }

    }

    @Test
    public void testSortOrder() {
        RSet<Integer> list = redisson.getSet("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        Set<Integer> descSort = list.readSort(SortOrder.DESC);
        assertThat(descSort).containsExactly(3, 2, 1);

        Set<Integer> ascSort = list.readSort(SortOrder.ASC);
        assertThat(ascSort).containsExactly(1, 2, 3);
    }
    
    @Test
    public void testSortOrderLimit() {
        RSet<Integer> list = redisson.getSet("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        Set<Integer> descSort = list.readSort(SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly(2, 1);

        Set<Integer> ascSort = list.readSort(SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly(2, 3);
    }

    @Test
    public void testSortOrderByPattern() {
        RSet<Integer> list = redisson.getSet("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        Set<Integer> descSort = list.readSort("test*", SortOrder.DESC);
        assertThat(descSort).containsExactly(1, 2, 3);

        Set<Integer> ascSort = list.readSort("test*", SortOrder.ASC);
        assertThat(ascSort).containsExactly(3, 2, 1);
    }
    
    @Test
    public void testSortOrderByPatternLimit() {
        RSet<Integer> list = redisson.getSet("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        Set<Integer> descSort = list.readSort("test*", SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly(2, 3);

        Set<Integer> ascSort = list.readSort("test*", SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly(2, 1);
    }

    @Test
    public void testSortOrderByPatternGet() {
        RSet<String> list = redisson.getSet("list", StringCodec.INSTANCE);
        list.add("1");
        list.add("2");
        list.add("3");
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(1);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(3);
        
        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");
        
        Collection<String> descSort = list.readSort("test*", Arrays.asList("tester*"), SortOrder.DESC);
        assertThat(descSort).containsExactly("obj3", "obj2", "obj1");

        Collection<String> ascSort = list.readSort("test*", Arrays.asList("tester*"), SortOrder.ASC);
        assertThat(ascSort).containsExactly("obj1", "obj2", "obj3");
    }
    
    @Test
    public void testSortOrderByPatternGetLimit() {
        RSet<String> list = redisson.getSet("list", StringCodec.INSTANCE);
        list.add("1");
        list.add("2");
        list.add("3");
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(1);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(3);
        
        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");
        
        Collection<String> descSort = list.readSort("test*", Arrays.asList("tester*"), SortOrder.DESC, 1, 2);
        assertThat(descSort).containsExactly("obj2", "obj1");

        Collection<String> ascSort = list.readSort("test*", Arrays.asList("tester*"), SortOrder.ASC, 1, 2);
        assertThat(ascSort).containsExactly("obj2", "obj3");
    }

    @Test
    public void testSortOrderAlpha(){
        RSet<String> set = redisson.getSet("list", StringCodec.INSTANCE);
        set.add("1");
        set.add("3");
        set.add("12");

        assertThat(set.readSortAlpha(SortOrder.ASC))
                .containsExactly("1", "12", "3");
        assertThat(set.readSortAlpha(SortOrder.DESC))
                .containsExactly("3", "12", "1");
    }

    @Test
    public void testSortOrderLimitAlpha(){
        RSet<String> set = redisson.getSet("list", StringCodec.INSTANCE);
        set.add("1");
        set.add("3");
        set.add("12");

        assertThat(set.readSortAlpha(SortOrder.DESC, 0, 2))
                .containsExactly("3", "12");
        assertThat(set.readSortAlpha(SortOrder.DESC, 1, 2))
                .containsExactly("12", "1");
    }

    @Test
    public void testSortOrderByPatternAlpha(){
        RSet<Integer> set = redisson.getSet("list", IntegerCodec.INSTANCE);
        set.add(1);
        set.add(2);
        set.add(3);

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        Collection<Integer> descSort = set
                .readSortAlpha("test*", SortOrder.DESC);
        assertThat(descSort).containsExactly(2, 1, 3);

        Collection<Integer> ascSort = set
                .readSortAlpha("test*", SortOrder.ASC);
        assertThat(ascSort).containsExactly(3, 1, 2);
    }

    @Test
    public void testSortOrderByPatternAlphaLimit(){
        RSet<Integer> set = redisson.getSet("list", IntegerCodec.INSTANCE);
        set.add(1);
        set.add(2);
        set.add(3);

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        Collection<Integer> descSort = set
                .readSortAlpha("test*", SortOrder.DESC,1, 2);
        assertThat(descSort).containsExactly(1, 3);

        Collection<Integer> ascSort = set
                .readSortAlpha("test*", SortOrder.ASC,1, 2);
        assertThat(ascSort).containsExactly(1, 2);
    }

    @Test
    public void testSortOrderByPatternGetAlpha() {
        RSet<String> set = redisson.getSet("list", StringCodec.INSTANCE);
        set.add("1");
        set.add("2");
        set.add("3");

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");

        Collection<String> descSort = set
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.DESC);
        assertThat(descSort).containsExactly("obj2", "obj1", "obj3");

        Collection<String> ascSort = set
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.ASC);
        assertThat(ascSort).containsExactly("obj3", "obj1", "obj2");
    }

    @Test
    public void testSortOrderByPatternGetAlphaLimit() {
        RSet<String> set = redisson.getSet("list", StringCodec.INSTANCE);
        set.add("1");
        set.add("2");
        set.add("3");

        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(12);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);

        redisson.getBucket("tester1", StringCodec.INSTANCE).set("obj1");
        redisson.getBucket("tester2", StringCodec.INSTANCE).set("obj2");
        redisson.getBucket("tester3", StringCodec.INSTANCE).set("obj3");

        Collection<String> descSort = set
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.DESC,1,  2);
        assertThat(descSort).containsExactly("obj1", "obj3");

        Collection<String> ascSort = set
                .readSortAlpha("test*", Arrays.asList("tester*"), SortOrder.ASC,1,  2);
        assertThat(ascSort).containsExactly("obj1", "obj2");
    }

    @Test
    public void testSortTo() {
        RSet<String> list = redisson.getSet("list", IntegerCodec.INSTANCE);
        list.add("1");
        list.add("2");
        list.add("3");

        assertThat(list.sortTo("test3", SortOrder.DESC)).isEqualTo(3);
        RList<String> list2 = redisson.getList("test3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("3", "2", "1");
        
        assertThat(list.sortTo("test4", SortOrder.ASC)).isEqualTo(3);
        RList<String> list3 = redisson.getList("test4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("1", "2", "3");

    }

    @Test
    public void testSortToLimit() {
        RSet<Integer> list = redisson.getSet("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        assertThat(list.sortTo("test3", SortOrder.DESC, 1, 2)).isEqualTo(2);
        RList<String> list2 = redisson.getList("test3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("2", "1");
        
        assertThat(list.sortTo("test4", SortOrder.ASC, 1, 2)).isEqualTo(2);
        RList<String> list3 = redisson.getList("test4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("2", "3");
    }

    @Test
    public void testSortToByPattern() {
        RSet<Integer> list = redisson.getSet("list", IntegerCodec.INSTANCE);
        list.add(1);
        list.add(2);
        list.add(3);
        
        redisson.getBucket("test1", IntegerCodec.INSTANCE).set(3);
        redisson.getBucket("test2", IntegerCodec.INSTANCE).set(2);
        redisson.getBucket("test3", IntegerCodec.INSTANCE).set(1);
        
        assertThat(list.sortTo("tester3", "test*", SortOrder.DESC, 1, 2)).isEqualTo(2);
        RList<String> list2 = redisson.getList("tester3", StringCodec.INSTANCE);
        assertThat(list2).containsExactly("2", "3");
        
        assertThat(list.sortTo("tester4", "test*", SortOrder.ASC, 1, 2)).isEqualTo(2);
        RList<String> list3 = redisson.getList("tester4", StringCodec.INSTANCE);
        assertThat(list3).containsExactly("2", "1");
    }

    
    @Test
    public void testRemoveRandom() {
        RSet<Integer> set = redisson.getSet("simple");
        set.add(1);
        set.add(2);
        set.add(3);

        assertThat(set.removeRandom()).isIn(1, 2, 3);
        assertThat(set.removeRandom()).isIn(1, 2, 3);
        assertThat(set.removeRandom()).isIn(1, 2, 3);
        assertThat(set.removeRandom()).isNull();
    }
    
    @Test
    public void testRemoveRandomAmount() {
        RSet<Integer> set = redisson.getSet("simple");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(6);

        assertThat(set.removeRandom(3)).isSubsetOf(1, 2, 3, 4, 5, 6).hasSize(3);
        assertThat(set.removeRandom(2)).isSubsetOf(1, 2, 3, 4, 5, 6).hasSize(2);
        assertThat(set.removeRandom(1)).isSubsetOf(1, 2, 3, 4, 5, 6).hasSize(1);
        assertThat(set.removeRandom(4)).isEmpty();
    }

    @Test
    public void testRandomLimited() {
        RSet<Integer> set = redisson.getSet("simple");
        for (int i = 0; i < 10; i++) {
            set.add(i);
        }
        
        assertThat(set.random(3)).containsAnyElementsOf(set.readAll()).hasSize(3);
    }
    
    
    @Test
    public void testRandom() {
        RSet<Integer> set = redisson.getSet("simple");
        set.add(1);
        set.add(2);
        set.add(3);

        assertThat(set.random()).isIn(1, 2, 3);
        assertThat(set.random()).isIn(1, 2, 3);
        assertThat(set.random()).isIn(1, 2, 3);
        assertThat(set).containsOnly(1, 2, 3);
    }

    @Test
    public void testAddBean() throws InterruptedException, ExecutionException {
        SimpleBean sb = new SimpleBean();
        sb.setLng(1L);
        RSet<SimpleBean> set = redisson.getSet("simple");
        set.add(sb);
        Assert.assertEquals(sb.getLng(), set.iterator().next().getLng());
    }

    @Test
    public void testAddLong() throws InterruptedException, ExecutionException {
        Long sb = 1l;

        RSet<Long> set = redisson.getSet("simple_longs");
        set.add(sb);

        for (Long l : set) {
            Assert.assertEquals(sb.getClass(), l.getClass());
        }

        Object[] arr = set.toArray();

        for (Object o : arr) {
            Assert.assertEquals(sb.getClass(), o.getClass());
        }
    }

    @Test
    public void testAddAsync() throws InterruptedException, ExecutionException {
        RSet<Integer> set = redisson.getSet("simple");
        RFuture<Boolean> future = set.addAsync(2);
        Assert.assertTrue(future.get());

        Assert.assertTrue(set.contains(2));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RSet<Integer> set = redisson.getSet("simple");
        set.add(1);
        set.add(3);
        set.add(7);

        Assert.assertTrue(set.removeAsync(1).get());
        Assert.assertFalse(set.contains(1));
        assertThat(set).containsOnly(3, 7);

        Assert.assertFalse(set.removeAsync(1).get());
        assertThat(set).containsOnly(3, 7);

        set.removeAsync(3).get();
        Assert.assertFalse(set.contains(3));
        assertThat(set).contains(7);
    }

    @Test
    public void testIteratorRemove() {
        Set<String> list = redisson.getSet("list");
        list.add("1");
        list.add("4");
        list.add("2");
        list.add("5");
        list.add("3");

        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            String value = iterator.next();
            if (value.equals("2")) {
                iterator.remove();
            }
        }

        assertThat(list).containsOnly("1", "4", "5", "3");

        int iteration = 0;
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            iterator.next();
            iterator.remove();
            iteration++;
        }

        Assert.assertEquals(4, iteration);

        Assert.assertEquals(0, list.size());
        Assert.assertTrue(list.isEmpty());
    }

    @Test
    public void testIteratorSequence() {
        Set<Long> set = redisson.getSet("set");
        for (int i = 0; i < 1000; i++) {
            set.add(Long.valueOf(i));
        }

        Set<Long> setCopy = new HashSet<Long>();
        for (int i = 0; i < 1000; i++) {
            setCopy.add(Long.valueOf(i));
        }

        checkIterator(set, setCopy);
    }

    private void checkIterator(Set<Long> set, Set<Long> setCopy) {
        for (Iterator<Long> iterator = set.iterator(); iterator.hasNext();) {
            Long value = iterator.next();
            if (!setCopy.remove(value)) {
                Assert.fail();
            }
        }

        Assert.assertEquals(0, setCopy.size());
    }

    @Test
    public void testLong() {
        Set<Long> set = redisson.getSet("set");
        set.add(1L);
        set.add(2L);

        assertThat(set).containsOnly(1L, 2L);
    }

    @Test
    public void testRetainAll() {
        Set<Integer> set = redisson.getSet("set");
        for (int i = 0; i < 20000; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.retainAll(Arrays.asList(1, 2)));
        assertThat(set).containsOnly(1, 2);
        Assert.assertEquals(2, set.size());
    }

    @Test
    public void testClusteredIterator() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave4 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave5 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave6 = new RedisRunner().randomPort().randomDir().nosave();
        
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1, slave4)
                .addNode(master2, slave2, slave5)
                .addNode(master3, slave3, slave6);

        ClusterProcesses process = clusterRunner.run();
        
        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
        int size = 10000;
        RSet<String> set = redisson.getSet("test");
        for (int i = 0; i < size; i++) {
            set.add("" + i);
        }
        
        Set<String> keys = new HashSet<>();
        for (String key : set) {
            keys.add(key);
        }
        
        assertThat(keys).hasSize(size);
        
        redisson.shutdown();
        process.shutdown();
    }
    
    @Test
    public void testIteratorRemoveHighVolume() throws InterruptedException {
        Set<Integer> set = redisson.getSet("set") /*new HashSet<Integer>()*/;
        for (int i = 0; i < 10000; i++) {
            set.add(i);
        }
        int cnt = 0;

        Iterator<Integer> iterator = set.iterator();
        while (iterator.hasNext()) {
            Integer integer = iterator.next();
            iterator.remove();
            cnt++;
        }
        Assert.assertEquals(0, set.size());
        Assert.assertEquals(10000, cnt);
    }

    @Test
    public void testContainsAll() {
        Set<Integer> set = redisson.getSet("set");
        for (int i = 0; i < 200; i++) {
            set.add(i);
        }

        Assert.assertTrue(set.containsAll(Collections.emptyList()));
        Assert.assertTrue(set.containsAll(Arrays.asList(30, 11)));
        Assert.assertFalse(set.containsAll(Arrays.asList(30, 711, 11)));
    }

    @Test
    public void testToArray() {
        Set<String> set = redisson.getSet("set");
        set.add("1");
        set.add("4");
        set.add("2");
        set.add("5");
        set.add("3");

        assertThat(set.toArray()).containsOnly("1", "2", "4", "5", "3");

        String[] strs = set.toArray(new String[0]);
        assertThat(strs).containsOnly("1", "2", "4", "5", "3");
    }

    @Test
    public void testContains() {
        Set<TestObject> set = redisson.getSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assert.assertTrue(set.contains(new TestObject("2", "3")));
        Assert.assertTrue(set.contains(new TestObject("1", "2")));
        Assert.assertFalse(set.contains(new TestObject("1", "9")));
    }

    @Test
    public void testDuplicates() {
        Set<TestObject> set = redisson.getSet("set");

        set.add(new TestObject("1", "2"));
        set.add(new TestObject("1", "2"));
        set.add(new TestObject("2", "3"));
        set.add(new TestObject("3", "4"));
        set.add(new TestObject("5", "6"));

        Assert.assertEquals(4, set.size());
    }

    @Test
    public void testSize() {
        Set<Integer> set = redisson.getSet("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(5);

        Assert.assertEquals(5, set.size());
    }


    @Test
    public void testRetainAllEmpty() {
        Set<Integer> set = redisson.getSet("set");
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);

        Assert.assertTrue(set.retainAll(Collections.<Integer>emptyList()));
        Assert.assertEquals(0, set.size());
    }

    @Test
    public void testRetainAllNoModify() {
        Set<Integer> set = redisson.getSet("set");
        set.add(1);
        set.add(2);

        Assert.assertFalse(set.retainAll(Arrays.asList(1, 2))); // nothing changed
        assertThat(set).containsOnly(1, 2);
    }

    @Test
    public void testUnion() {
        RSet<Integer> set = redisson.getSet("set");
        set.add(5);
        set.add(6);
        RSet<Integer> set1 = redisson.getSet("set1");
        set1.add(1);
        set1.add(2);
        RSet<Integer> set2 = redisson.getSet("set2");
        set2.add(3);
        set2.add(4);

        assertThat(set.union("set1", "set2")).isEqualTo(4);
        assertThat(set).containsOnly(1, 2, 3, 4);
    }

    @Test
    public void testReadUnion() {
        RSet<Integer> set = redisson.getSet("set");
        set.add(5);
        set.add(6);
        RSet<Integer> set1 = redisson.getSet("set1");
        set1.add(1);
        set1.add(2);
        RSet<Integer> set2 = redisson.getSet("set2");
        set2.add(3);
        set2.add(4);

        assertThat(set.readUnion("set1", "set2")).containsOnly(1, 2, 3, 4, 5, 6);
        assertThat(set).containsOnly(5, 6);
    }

    @Test
    public void testDiff() {
        RSet<Integer> set = redisson.getSet("set");
        set.add(5);
        set.add(6);
        RSet<Integer> set1 = redisson.getSet("set1");
        set1.add(1);
        set1.add(2);
        set1.add(3);
        RSet<Integer> set2 = redisson.getSet("set2");
        set2.add(3);
        set2.add(4);
        set2.add(5);

        assertThat(set.diff("set1", "set2")).isEqualTo(2);
        assertThat(set).containsOnly(1, 2);
    }

    @Test
    public void testReadDiff() {
        RSet<Integer> set = redisson.getSet("set");
        set.add(5);
        set.add(7);
        set.add(6);
        RSet<Integer> set1 = redisson.getSet("set1");
        set1.add(1);
        set1.add(2);
        set1.add(5);
        RSet<Integer> set2 = redisson.getSet("set2");
        set2.add(3);
        set2.add(4);
        set2.add(5);

        assertThat(set.readDiff("set1", "set2")).containsOnly(7, 6);
        assertThat(set).containsOnly(6, 5, 7);
    }

    @Test
    public void testIntersection() {
        RSet<Integer> set = redisson.getSet("set");
        set.add(5);
        set.add(6);
        RSet<Integer> set1 = redisson.getSet("set1");
        set1.add(1);
        set1.add(2);
        set1.add(3);
        RSet<Integer> set2 = redisson.getSet("set2");
        set2.add(3);
        set2.add(4);
        set2.add(5);

        assertThat(set.intersection("set1", "set2")).isEqualTo(1);
        assertThat(set).containsOnly(3);
    }

    @Test
    public void testReadIntersection() {
        RSet<Integer> set = redisson.getSet("set");
        set.add(5);
        set.add(7);
        set.add(6);
        RSet<Integer> set1 = redisson.getSet("set1");
        set1.add(1);
        set1.add(2);
        set1.add(5);
        RSet<Integer> set2 = redisson.getSet("set2");
        set2.add(3);
        set2.add(4);
        set2.add(5);

        assertThat(set.readIntersection("set1", "set2")).containsOnly(5);
        assertThat(set).containsOnly(6, 5, 7);
    }

    
    @Test
    public void testMove() throws Exception {
        RSet<Integer> set = redisson.getSet("set");
        RSet<Integer> otherSet = redisson.getSet("otherSet");

        set.add(1);
        set.add(2);

        assertThat(set.move("otherSet", 1)).isTrue();

        assertThat(set.size()).isEqualTo(1);
        assertThat(set).contains(2);

        assertThat(otherSet.size()).isEqualTo(1);
        assertThat(otherSet).contains(1);
    }

    @Test
    public void testMoveNoMember() throws Exception {
        RSet<Integer> set = redisson.getSet("set");
        RSet<Integer> otherSet = redisson.getSet("otherSet");

        set.add(1);

        Assert.assertFalse(set.move("otherSet", 2));

        Assert.assertEquals(1, set.size());
        Assert.assertEquals(0, otherSet.size());
    }
    
    
    @Test
    public void testRemoveAllEmpty() {
        Set<Integer> list = redisson.getSet("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assert.assertFalse(list.removeAll(Collections.emptyList()));
        Assert.assertFalse(Arrays.asList(1).removeAll(Collections.emptyList()));
    }

    @Test
    public void testRemoveAll() {
        Set<Integer> list = redisson.getSet("list");
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        Assert.assertFalse(list.removeAll(Collections.emptyList()));
        Assert.assertTrue(list.removeAll(Arrays.asList(3, 2, 10, 6)));

        assertThat(list).containsExactlyInAnyOrder(1, 4, 5);

        Assert.assertTrue(list.removeAll(Arrays.asList(4)));

        assertThat(list).containsExactly(1, 5);

        Assert.assertTrue(list.removeAll(Arrays.asList(1, 5, 1, 5)));

        Assert.assertTrue(list.isEmpty());
    }
}
