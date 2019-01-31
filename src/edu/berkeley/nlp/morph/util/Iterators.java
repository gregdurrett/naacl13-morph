package edu.berkeley.nlp.morph.util;

import java.util.Iterator;

public class Iterators
{
	/**
	 * Wraps an iterator as an iterable
	 * 
	 * @param <T>
	 * @param it
	 * @return
	 */
	public static <T> Iterable<T> able(final Iterator<T> it) {
		return new Iterable<T>()
		{
			boolean used = false;

			public Iterator<T> iterator() {
				if (used) throw new RuntimeException("One use iterable");
				used = true;
				return it;
			}
		};
	}
}
