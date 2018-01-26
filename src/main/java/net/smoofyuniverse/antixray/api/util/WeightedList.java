/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Hugo Dupanloup (Yeregorix)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.smoofyuniverse.antixray.api.util;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

public final class WeightedList<T> {
	private final T[] objects;
	private final double[] weights;
	private final int size;
	private final double totalWeight;

	private WeightedList(T[] objects, double[] weights, double total) {
		this.objects = objects;
		this.weights = weights;
		this.size = objects.length;
		this.totalWeight = total;
	}

	public int getSize() {
		return this.size;
	}

	public T get(Random r) {
		double d = r.nextDouble() * this.totalWeight;
		int i = -1;
		while (d >= 0) {
			i++;
			d -= this.weights[i];
		}
		return this.objects[i];
	}

	public void forEach(Consumer<T> c) {
		for (int i = 0; i < this.size; i++)
			c.accept(this.objects[i], this.weights[i]);
	}

	public static <T> WeightedList<T> of(T[] objects, double[] weights) {
		if (objects.length != weights.length || objects.length == 0)
			throw new IllegalArgumentException("Size");

		double total = 0;
		for (double w : weights)
			total += w;

		return new WeightedList<>(objects.clone(), weights.clone(), total);
	}

	@SuppressWarnings("unchecked")
	public static <T> WeightedList<T> of(Map<T, Double> map) {
		if (map.isEmpty())
			throw new IllegalArgumentException("Size");

		T[] objects = (T[]) new Object[map.size()];
		double[] weights = new double[map.size()];

		int i = 0;
		for (Entry<T, Double> e : map.entrySet()) {
			objects[i] = e.getKey();
			weights[i] = e.getValue();
			i++;
		}

		double total = 0;
		for (double w : weights)
			total += w;

		return new WeightedList<>(objects, weights, total);
	}

	public interface Consumer<T> {
		void accept(T object, double weight);
	}
}
