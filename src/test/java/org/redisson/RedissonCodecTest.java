package org.redisson;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.codec.Codec;
import org.redisson.codec.CborJacksonCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.KryoCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.core.RMap;

public class RedissonCodecTest extends BaseTest {
	private Codec codec = new SerializationCodec();
	private Codec kryoCodec = new KryoCodec();
	private Codec jsonCodec = new JsonJacksonCodec();
	private Codec cborCodec = new CborJacksonCodec();

	@Test
	public void testJdk() {
		Config config = createConfig();
		config.setCodec(codec);
		redisson = Redisson.create(config);

		test();
	}

	@Test
	public void testJson() {
		Config config = createConfig();
		config.setCodec(jsonCodec);
		redisson = Redisson.create(config);

		test();
	}

	public void test() {
		RMap<Integer, Map<String, Object>> map = redisson.getMap("getAll");
		Map<String, Object> a = new HashMap<String, Object>();
		a.put("double", new Double(100000.0));
		a.put("float", 100.0f);
		a.put("int", 100);
		a.put("long", 10000000000L);
		a.put("boolt", true);
		a.put("boolf", false);
		a.put("string", "testString");
		a.put("array", Arrays.asList(1, 2.0, "adsfasdfsdf"));

		map.fastPut(1, a);
		Map<String, Object> resa = map.get(1);
		Assert.assertEquals(a, resa);

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

	// @Test
	public void testKryo() {
		Config config = createConfig();
		config.setCodec(kryoCodec);
		redisson = Redisson.create(config);

		test();
	}

	@Test
	public void testCbor() {
		Config config = createConfig();
		config.setCodec(cborCodec);
		redisson = Redisson.create(config);

		test();

	}
}
