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
 * Represents a mutable client-side chunk.
 */
public interface ChunkView extends BlockView, OpaqueChunk {

	@Override
	ChunkStorage storage();

	/**
	 * @return The state of this chunk.
	 */
	State state();

	/**
	 * If not already done and if all modifiers are ready, this method obfuscates all blocks inside this chunk.
	 */
	void obfuscate();

	/**
	 * If the chunk marked as obfuscated, this method deofuscates all blocks inside this chunk.
	 */
	void deobfuscate();

	/**
	 * Deofuscates and then obfuscates all blocks inside this chunk.
	 */
	void reobfuscate();

	/**
	 * Resets the dynamism distance of all positions
	 */
	void clearDynamism();

	public enum State {
		/**
		 * All blocks in the chunk are visible.
		 */
		DEOBFUSCATED,
		/**
		 * All blocks in the chunk are visible.
		 * The chunk will be obfuscated as soon as possible.
		 */
		OBFUSCATION_REQUESTED,
		/**
		 * All modifiers have been applied.
		 */
		OBFUSCATED
	}
}
