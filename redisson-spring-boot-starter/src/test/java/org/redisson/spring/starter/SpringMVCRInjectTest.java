package org.redisson.spring.starter;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.RedisRunner;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;


@RunWith(SpringJUnit4ClassRunner.class)
@WebMvcTest
@AutoConfigureMockMvc
public class SpringMVCRInjectTest {

    @Autowired
    private MockMvc mockMvc;
    @RInject
    private RedissonClient redissonClient;

    @BeforeClass
    public static void init() throws Exception {
        RedisRunner.startDefaultRedisServerInstance();
        System.setProperty("redisAddress", RedisRunner.getDefaultRedisServerBindAddressAndPort());
        System.setProperty("my", "my");
        System.setProperty("map", "randomMap");
    }

    @AfterClass
    public static void destroy() throws Exception {
        RedisRunner.shutDownDefaultRedisServerInstance();
    }

    @Test
    public void testGetRMap() throws Exception {
        assertNotNull(this.redissonClient);
        String v = UUID.randomUUID().toString();
        RMap myMap = redissonClient.getMap("myMap");
        RList myList = redissonClient.getList("myList");
        RSet mySet = redissonClient.getSet("mySet");
        RMap myMap1 = redissonClient.getMap("my:randomMap");

        mockMvc.perform(get("/getMap/" + v))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andDo(print());
        assertEquals(v, myMap.get("getRMap"));

        mockMvc.perform(get("/getList/" + v))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andDo(print());
        assertEquals(v, myList.get(0));

        mockMvc.perform(get("/getSet/" + v))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andDo(print());
        assertEquals(v, mySet.iterator().next());

        mockMvc.perform(get("/getMap1/" + v))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andDo(print());
        assertEquals(v, myMap1.get("getRMap1"));

    }

}
