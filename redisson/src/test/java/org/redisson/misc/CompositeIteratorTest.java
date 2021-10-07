package org.redisson.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;


/**
 * @author Pepe Lu
 */
public class CompositeIteratorTest {

	@Test
	public void testHasNextWithEmpty() {
		List<Integer> emptyList = new ArrayList<Integer>();
		CompositeIterable<Integer> compositeIterable = new CompositeIterable<Integer>(
				emptyList);
		assertThat(compositeIterable.iterator().hasNext()).isFalse();
	}

	@Test
	public void testNextWithEmpty() {
		Assertions.assertThrows(NoSuchElementException.class, () -> {
			List<Integer> emptyList = new ArrayList<Integer>();
			CompositeIterable<Integer> compositeIterable = new CompositeIterable<Integer>(
					emptyList);
			compositeIterable.iterator().next();
		});
	}

	@Test
	public void testNextWithOne() {
		List<Integer> list = Arrays.asList(1, 2);
		CompositeIterable<Integer> compositeIterable = new CompositeIterable<Integer>(
				list);
		Iterator<Integer> iterator = compositeIterable.iterator();
		assertThat(iterator.next()).isEqualTo(1);
		assertThat(iterator.next()).isEqualTo(2);
		assertThat(iterator.hasNext()).isFalse();
	}

	@Test
	public void testNextWithTwo() {
		List<Integer> list1 = Arrays.asList(1, 2);
		List<Integer> list2 = Arrays.asList(3, 4);
		CompositeIterable<Integer> compositeIterable = new CompositeIterable<Integer>(
				list1, list2);
		Iterator<Integer> iterator = compositeIterable.iterator();
		assertThat(iterator.next()).isEqualTo(1);
		assertThat(iterator.next()).isEqualTo(2);
		assertThat(iterator.next()).isEqualTo(3);
		assertThat(iterator.next()).isEqualTo(4);
		assertThat(iterator.hasNext()).isFalse();
	}

	@Test
	public void testRemoveWithEmpty() {
		Assertions.assertThrows(IllegalStateException.class, () -> {
			List<Integer> emptyList = new ArrayList<>();
			CompositeIterable<Integer> compositeIterable = new CompositeIterable<Integer>(
					emptyList);
			compositeIterable.iterator().remove();
		});
	}

	@Test
	public void testRemoveWithOne() {
		/**
		 * use ArrayList instead of Arrays.asList() because Arrays.asList() is using
		 * {@link Arrays.ArrayList} which is using {@link AbstractList.Itr} as its
		 * iterator. And this iterator does not implement remove()
		 */
		List<Integer> list = new ArrayList<Integer>();
		list.add(1);
		list.add(2);
		CompositeIterable<Integer> compositeIterable = new CompositeIterable<Integer>(
				list);
		Iterator<Integer> iterator = compositeIterable.iterator();
		int count = list.size();
		while (iterator.hasNext()) {
			iterator.next();
			iterator.remove();
			count--;
		}

		assertThat(count).isEqualTo(0);
		assertThat(list.size()).isEqualTo(0);
	}

	@Test
	public void testRemoveWithTwo() {
		List<Integer> list1 = new ArrayList<Integer>();
		list1.add(1);
		list1.add(2);
		List<Integer> list2 = new ArrayList<Integer>();
		list2.add(3);
		list2.add(4);
		CompositeIterable<Integer> compositeIterable = new CompositeIterable<Integer>(
				list1, list2);
		Iterator<Integer> iterator = compositeIterable.iterator();
		int count = list1.size() + list2.size();
		while (iterator.hasNext()) {
			iterator.next();
			iterator.remove();
			count--;
		}

		assertThat(count).isEqualTo(0);
        assertThat(list1.size()).isEqualTo(0);
        assertThat(list2.size()).isEqualTo(0);
	}

	@Test
    public void testPartialIterationWithTwo(){
        List<Integer> list1 = new ArrayList<Integer>();
        list1.add(1);
        list1.add(2);
        List<Integer> list2 = new ArrayList<Integer>();
        list2.add(3);
        list2.add(4);
        CompositeIterable<Integer> compositeIterable = new CompositeIterable<Integer>(
                list1, list2);
        Iterator<Integer> iterator = compositeIterable.iterator();
        iterator.next();
        iterator.remove();
        iterator.next();
        iterator.next();
        iterator.remove();
        assertThat(list1.size()).isEqualTo(1);
        assertThat(list2.size()).isEqualTo(1);
        assertThat(list1.get(0)).isEqualTo(2);
        assertThat(list2.get(0)).isEqualTo(4);
    }
}