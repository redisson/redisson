package org.redisson;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.redisson.rule.RedisServerRule;
import org.redisson.rule.RedissonRule;

/**
 * @author Philipp Marx
 */
public abstract class AbstractBaseTest {

    @ClassRule
    public static RedisServerRule redisServerRule = new RedisServerRule();

    @ClassRule
    public static RedissonRule redissonRule = new RedissonRule();

    @Rule
    public ExternalResource cleanup = new ExternalResource() {
        private Description description;

        public Statement apply(Statement base, Description description) {
            this.description = description;
            return super.apply(base, description);
        }

        protected void before() throws Throwable {
            redissonRule.setTestName(description.getMethodName());
        }

        protected void after() {
            redissonRule.cleanup();
        }
    };
}
