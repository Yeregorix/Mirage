/*
 * Copyright (c) 2018-2021 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.mirage.api.volume;

/**
 * A BlockStorage is an ImmutableBlockVolume associated with mutable BlockView. This object is used to represent a server-side BlockVolume.
 */
public interface BlockStorage extends OpaqueBlockVolume {

	/**
	 * Gets the world associated to this volume.
	 *
	 * @return The world
	 */
	WorldStorage world();

	/**
	 * @return The BlockView which is associated with this BlockStorage
	 * @throws IllegalStateException if the BlockView is not available
	 * @see BlockStorage#isViewAvailable
	 */
	BlockView view();

	/**
	 * @return Whether the BlockView associated with this BlockStorage is available
	 */
	boolean isViewAvailable();
}
