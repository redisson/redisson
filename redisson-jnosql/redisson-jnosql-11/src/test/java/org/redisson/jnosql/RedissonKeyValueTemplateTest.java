package org.redisson.jnosql;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Inject;
import org.eclipse.jnosql.communication.keyvalue.BucketManagerFactory;
import org.eclipse.jnosql.mapping.keyvalue.KeyValueDatabase;
import org.eclipse.jnosql.mapping.keyvalue.KeyValueTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RList;
import org.redisson.api.RSet;
import org.redisson.client.codec.StringCodec;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonKeyValueTemplateTest extends BaseTest {

    @Dependent
    public static class Library {

        @Inject
        KeyValueTemplate template;

        @Inject
        BucketManagerFactory factory;

        @Inject
        @KeyValueDatabase("names")
        List<String> names;

        @Inject
        BookRepository repository;

    }

    @BeforeEach
    void setProperties() {
        System.setProperty("jnosql.keyvalue.provider", RedissonKeyValueConfiguration.class.getName());
        System.setProperty("jnosql.keyvalue.database", "books");
        System.setProperty("jnosql.redisson.singleServerConfig.address", address());
        System.setProperty("jnosql.redisson.singleServerConfig.clientName", "jnosql");
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("jnosql.keyvalue.provider");
        System.clearProperty("jnosql.keyvalue.database");
        System.clearProperty("jnosql.redisson.singleServerConfig.address");
        System.clearProperty("jnosql.redisson.singleServerConfig.clientName");
    }

    @Test
    public void testTemplate() throws IOException, InterruptedException {
        assertThat(connections()).isZero();

        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            Library library = container.select(Library.class).get();
            Book dune = new Book("9780441013593", "Dune", 1965);
            library.template.put(dune, Duration.ofMinutes(1));

            assertThat(library.template.get("9780441013593", Book.class))
                    .get().usingRecursiveComparison().isEqualTo(dune);
            assertThat(redisson.getBucket("books:9780441013593").remainTimeToLive()).isPositive();

            library.template.delete(Book.class, "9780441013593");
            assertThat(library.template.get("9780441013593", Book.class)).isEmpty();

            Book emma = new Book("9780141439587", "Emma", 1815);
            library.repository.save(emma);
            assertThat(library.repository.findById("9780141439587")).get().usingRecursiveComparison().isEqualTo(emma);
            assertThat(redisson.getBucket("books:9780141439587", StringCodec.INSTANCE).isExists()).isTrue();
            library.repository.deleteById("9780141439587");
            assertThat(library.repository.findById("9780141439587")).isEmpty();

            library.names.add("Paul");
            assertThat(redisson.getList("names", StringCodec.INSTANCE).readAll()).containsExactly("Paul");
            assertThat(library.names).isInstanceOf(RList.class);
            assertThat(library.factory.getSet("tags", String.class)).isInstanceOf(RSet.class);
            assertThat(connections()).isPositive();
        }

        await().atMost(Duration.ofSeconds(10)).until(() -> connections() == 0);
    }

    private static long connections() throws IOException, InterruptedException {
        String clients = REDIS.execInContainer("redis-cli", "CLIENT", "LIST").getStdout();
        return clients.lines().filter(line -> line.contains(" name=jnosql ")).count();
    }

}
