package org.redisson.spring.starter;

import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class TestMvcController {
    @RequestMapping("/getMap/{name}")
    public String getRMap(
            @RInject("myMap") RMap map,
            @PathVariable String name) {
        map.put("getRMap", name);
        return "index";
    }

    @RequestMapping("/getList/{name}")
    public String getRList(
            @RInject("RList('myList')") List list,
            @PathVariable String name) {
        list.add(name);
        return "index";
    }

    @RequestMapping("/getSet/{name}")
    public String getRSet(
            @RInject("#{@redisson}") RedissonClient client,
            @PathVariable String name) {
        Set set = client.getSet("mySet");
        set.add(name);
        return "index";
    }

    @RequestMapping("/getMap1/{name}")
    public String getRMap1(
            @RInject("RMap('#{ systemProperties['my'] + ':' + '${map}' }')") Map map,
            @PathVariable String name) {
        map.put("getRMap1", name);
        return "index";
    }
}
