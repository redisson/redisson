package org.redisson;

import io.netty.util.concurrent.Future;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.redisson.core.RScript;

public class RedissonScriptTest extends BaseTest {

    @Test
    public void testEval() {
        RScript script = redisson.getScript();
        List<Object> res = script.eval("return {1,2,3.3333,'foo',nil,'bar'}", RScript.ReturnType.MULTI, Collections.emptyList());
        MatcherAssert.assertThat(res, Matchers.<Object>contains(1L, 2L, 3L, "foo"));
    }

    @Test
    public void testEvalAsync() {
        RScript script = redisson.getScript();
        Future<List<Object>> res = script.evalAsync("return {1,2,3.3333,'foo',nil,'bar'}", RScript.ReturnType.MULTI, Collections.emptyList());
        MatcherAssert.assertThat(res.awaitUninterruptibly().getNow(), Matchers.<Object>contains(1L, 2L, 3L, "foo"));
    }
    
}
