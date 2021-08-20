package org.redisson.micronaut;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.session.event.AbstractSessionEvent;
import io.micronaut.session.event.SessionCreatedEvent;
import io.micronaut.session.event.SessionDeletedEvent;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.micronaut.session.RedissonSession;
import org.redisson.micronaut.session.RedissonSessionStore;

import javax.inject.Singleton;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Nikita Koksharov
 */
public class RedissonSessionTest {

    public static class MyObject implements Serializable {

        private String name;

        public MyObject() {
        }

        public MyObject(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Singleton
    public static class AppListener implements ApplicationEventListener<AbstractSessionEvent> {

        List<AbstractSessionEvent> events = new ArrayList<>();

        @Override
        public void onApplicationEvent(AbstractSessionEvent event) {
            events.add(event);
        }

        public List<AbstractSessionEvent> getEvents() {
            return events;
        }
    }

    @Test
    public void testWriteBehind() throws ExecutionException, InterruptedException {
        Map<String, Object> map = new HashMap<>();
        map.put("redisson.threads", "10");
        map.put("micronaut.session.http.redisson.enabled", "true");
        map.put("micronaut.session.http.redisson.updateMode", "WRITE_BEHIND");

        map.put("redisson.singleServerConfig.address", "redis://127.0.0.1:6379");
        ApplicationContext ac = ApplicationContext.run(map);
        RedissonClient rc = ac.getBean(RedissonClient.class);

        rc.getKeys().flushall();

        RedissonSessionStore sessionStore = ac.getBean(RedissonSessionStore.class);
        RedissonSession session = sessionStore.newSession();
        session.put("key1", "oleg");
        session.put("key2", new MyObject("myname"));
        session.setMaxInactiveInterval(Duration.ofSeconds(30));

        RedissonSession saved = sessionStore.save(session).get();

        saved.remove("key2");
        saved.put("key1", "alba");

        RedissonSession s = sessionStore.findSession(saved.getId()).get().get();
        assertThat(s.get("key1").get()).isEqualTo("alba");
        assertThat(s.contains("key2")).isFalse();

        ac.stop();
    }

    @Test
    public void testSessionExpiration() throws ExecutionException, InterruptedException {
        Map<String, Object> map = new HashMap<>();
        map.put("redisson.threads", "10");
        map.put("micronaut.session.http.redisson.enabled", "true");
        map.put("redisson.singleServerConfig.address", "redis://127.0.0.1:6379");
        ApplicationContext ac = ApplicationContext.run(map);
        RedissonClient rc = ac.getBean(RedissonClient.class);

        rc.getKeys().flushall();

        RedissonSessionStore sessionStore = ac.getBean(RedissonSessionStore.class);
        RedissonSession session = sessionStore.newSession();
        session.put("username", "oleg");
        session.put("foo", new MyObject("myname"));
        session.setMaxInactiveInterval(Duration.ofSeconds(30));

        RedissonSession saved = sessionStore.save(session).get();
        testData(saved);

        Thread.sleep(30500);

        Optional<RedissonSession> noSession = sessionStore.findSession(saved.getId()).get();
        assertThat(noSession).isEmpty();

        Thread.sleep(10000);

        assertThat(rc.getKeys().count()).isZero();

        ac.stop();
    }

    @Test
    public void testSessionCreate() throws ExecutionException, InterruptedException {
        Map<String, Object> map = new HashMap<>();
        map.put("redisson.threads", "10");
        map.put("micronaut.session.http.redisson.enabled", "true");
        map.put("redisson.singleServerConfig.address", "redis://127.0.0.1:6379");
        ApplicationContext ac = ApplicationContext.run(map);
        RedissonClient rc = ac.getBean(RedissonClient.class);
        AppListener listener = ac.getBean(AppListener.class);

        rc.getKeys().flushall();

        RedissonSessionStore sessionStore = ac.getBean(RedissonSessionStore.class);
        RedissonSession session = sessionStore.newSession();
        session.put("username", "oleg");
        session.put("foo", new MyObject("myname"));

        RedissonSession saved = sessionStore.save(session).get();
        testData(saved);

        assertThat(listener.getEvents()).hasSize(1);
        assertThat(listener.getEvents().get(0)).isInstanceOf(SessionCreatedEvent.class);

        listener.getEvents().clear();
        RedissonSession loaded = sessionStore.findSession(saved.getId()).get().get();
        testData(loaded);

        loaded.put("key", "value");
        loaded.remove("username");
        loaded.setLastAccessedTime(Instant.now());
        loaded.setMaxInactiveInterval(Duration.ofMinutes(1));
        sessionStore.save(loaded).get();

        assertThat(listener.getEvents()).isEmpty();

        loaded = sessionStore.findSession(saved.getId()).get().get();

        assertThat(listener.getEvents()).isEmpty();
        assertThat(loaded.contains("username")).isFalse();
        assertThat(((MyObject) loaded.get("foo").get()).getName()).isEqualTo("myname");
        assertThat(loaded.get("key").get()).isEqualTo("value");
        assertThat(loaded.isExpired()).isFalse();
        assertThat(loaded.getCreationTime().getEpochSecond()).isEqualTo(saved.getCreationTime().getEpochSecond());
        assertThat(loaded.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(loaded.getId()).isEqualTo(saved.getId());

        Boolean deleted = sessionStore.deleteSession(saved.getId()).get();
        assertThat(deleted).isTrue();

        Thread.sleep(1500);

        assertThat(listener.getEvents()).hasSize(1);
        assertThat(listener.getEvents().get(0)).isInstanceOf(SessionDeletedEvent.class);

        Optional<RedissonSession> noSession = sessionStore.findSession(saved.getId()).get();
        assertThat(noSession).isEmpty();

        Thread.sleep(10000);

        assertThat(rc.getKeys().count()).isZero();

        ac.stop();
    }

    private void testData(RedissonSession saved) {
        assertThat(saved.get("username").get()).isEqualTo("oleg");
        assertThat(((MyObject) saved.get("foo").get()).getName()).isEqualTo("myname");
        assertThat(saved.isExpired()).isFalse();
        assertThat(saved.getCreationTime()).isNotNull();
        assertThat(saved.getMaxInactiveInterval()).isNotNull();
        assertThat(saved.getId()).isNotNull();
    }


}
