package org.redisson;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class RedissonListTest {

    @Test
    public void testSize() {
        Redisson redisson = Redisson.create();
        List<String> list = redisson.getList("list");

        list.add("1");
        list.add("2");
        list.add("3");
        list.add("4");
        list.add("5");
        list.add("6");
        Assert.assertThat(list, Matchers.contains("1", "2", "3", "4", "5", "6"));

        list.remove("2");
        Assert.assertThat(list, Matchers.contains("1", "3", "4", "5", "6"));

        clear(list);
    }

    private void clear(List<?> list) {
        list.clear();
        Assert.assertEquals(0, list.size());
    }


}
