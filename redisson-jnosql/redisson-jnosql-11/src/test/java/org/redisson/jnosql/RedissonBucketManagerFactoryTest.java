package org.redisson.jnosql;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonBucketManagerFactoryTest extends BaseTest {

    @Test
    public void testList() {
        RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(redisson);
        List<Book> books = factory.getList("books", Book.class);
        books.add(new Book("1", "Dune", 1965));
        books.add(new Book("2", "Emma", 1815));
        factory.getList("names", String.class).add("Paul");

        assertThat(factory.getList("books", Book.class)).extracting(Book::getTitle).containsExactly("Dune", "Emma");
        assertThat(redisson.getList("books", StringCodec.INSTANCE).readAll()).containsExactly(
                "{\"isbn\":\"1\",\"title\":\"Dune\",\"year\":1965}",
                "{\"isbn\":\"2\",\"title\":\"Emma\",\"year\":1815}");
        assertThat(redisson.getList("names", StringCodec.INSTANCE).readAll()).containsExactly("Paul");
    }

    @Test
    public void testSet() {
        RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(redisson);
        Set<Book> books = factory.getSet("books", Book.class);
        books.add(new Book("1", "Dune", 1965));
        books.add(new Book("1", "Dune", 1965));
        factory.getSet("names", String.class).add("Paul");

        assertThat(factory.getSet("books", Book.class)).extracting(Book::getTitle).containsExactly("Dune");
        assertThat(redisson.getSet("books", StringCodec.INSTANCE).readAll())
                .containsExactly("{\"isbn\":\"1\",\"title\":\"Dune\",\"year\":1965}");
        assertThat(redisson.getSet("names", StringCodec.INSTANCE).readAll()).containsExactly("Paul");
    }

    @Test
    public void testQueue() {
        RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(redisson);
        Queue<Book> books = factory.getQueue("books", Book.class);
        books.offer(new Book("1", "Dune", 1965));
        books.offer(new Book("2", "Emma", 1815));

        assertThat(factory.getQueue("books", Book.class).poll()).extracting(Book::getTitle).isEqualTo("Dune");
        assertThat(redisson.getQueue("books", StringCodec.INSTANCE).readAll())
                .containsExactly("{\"isbn\":\"2\",\"title\":\"Emma\",\"year\":1815}");
    }

    @Test
    public void testMap() {
        RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(redisson);
        Map<String, Book> books = factory.getMap("books", String.class, Book.class);
        books.put("1", new Book("1", "Dune", 1965));
        factory.getMap("years", Integer.class, String.class).put(1965, "Dune");

        assertThat(factory.getMap("books", String.class, Book.class).get("1"))
                .extracting(Book::getTitle).isEqualTo("Dune");
        assertThat(redisson.getMap("books", StringCodec.INSTANCE).readAllMap())
                .containsExactly(Map.entry("1", "{\"isbn\":\"1\",\"title\":\"Dune\",\"year\":1965}"));
        assertThat(redisson.getMap("years", StringCodec.INSTANCE).readAllMap())
                .containsExactly(Map.entry("1965", "Dune"));
    }

    @Test
    public void testCodecsOfSameTypeAreEqual() {
        RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(redisson);

        assertThat(factory.getList("a", Book.class).getCodec())
                .isEqualTo(factory.getSet("b", Book.class).getCodec())
                .hasSameHashCodeAs(factory.getQueue("c", Book.class).getCodec())
                .isNotEqualTo(factory.getList("a", Integer.class).getCodec());
        assertThat(factory.getMap("a", Integer.class, Book.class).getCodec())
                .isEqualTo(factory.getMap("b", Integer.class, Book.class).getCodec())
                .hasSameHashCodeAs(factory.getMap("c", Integer.class, Book.class).getCodec())
                .isNotEqualTo(factory.getMap("a", Book.class, Integer.class).getCodec());
    }

    @Test
    public void testCloseShutsDownOwnedClient() {
        RedissonClient client = Redisson.create(config());
        RedissonBucketManagerFactory factory = new RedissonBucketManagerFactory(client, true);
        RedissonBucketManager manager = factory.apply("books");
        manager.put("1", "a");

        factory.close();

        assertThat(client.isShutdown()).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> factory.apply("books"));
        manager.close();
        factory.close();
    }

    @Test
    public void testLastManagerShutsDownOwnedClient() {
        RedissonClient client = Redisson.create(config());
        RedissonBucketManagerFactory factory = new RedissonBucketManagerFactory(client, true);
        RedissonBucketManager books = factory.apply("books");
        RedissonBucketManager authors = factory.apply("authors");

        books.close();
        books.close();
        authors.put("1", "a");
        assertThat(client.isShutdown()).isFalse();

        authors.close();
        assertThat(client.isShutdown()).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> factory.apply("books"));
    }

    @Test
    public void testExternalClientIsNotShutDown() {
        RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(redisson);
        RedissonBucketManager manager = factory.apply("books");

        manager.close();
        factory.close();

        assertThat(redisson.isShutdown()).isFalse();
    }

    @Test
    public void testNullArguments() {
        RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(redisson);

        assertThatNullPointerException().isThrownBy(() -> factory.apply(null));
        assertThatNullPointerException().isThrownBy(() -> factory.getList(null, String.class));
        assertThatNullPointerException().isThrownBy(() -> factory.getList("books", null));
        assertThatNullPointerException().isThrownBy(() -> factory.getSet(null, String.class));
        assertThatNullPointerException().isThrownBy(() -> factory.getSet("books", null));
        assertThatNullPointerException().isThrownBy(() -> factory.getQueue(null, String.class));
        assertThatNullPointerException().isThrownBy(() -> factory.getQueue("books", null));
        assertThatNullPointerException().isThrownBy(() -> factory.getMap(null, String.class, String.class));
        assertThatNullPointerException().isThrownBy(() -> factory.getMap("books", null, String.class));
        assertThatNullPointerException().isThrownBy(() -> factory.getMap("books", String.class, null));
    }

}
