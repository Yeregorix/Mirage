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

package net.smoofyuniverse.antixray.api;

import co.aikar.timings.Timing;
import net.smoofyuniverse.antixray.api.volume.ChunkView;

import java.util.Random;

/**
 * This object is used to modify chunk per chunk the view of the world sent to players.
 */
public interface ViewModifier {

	/**
	 * @return The name of this modifier
	 */
	String getName();

	/**
	 * @return A Timing that will be used to monitor performances of this modifier
	 */
	Timing getTiming();

	/**
	 * A fast method to check whether this modifier is ready to modify a chunk.
	 * Some modifiers might need to check whether neighboring chunks are loaded.
	 * @param view The ChunkView to modify
	 * @return true if this modifier is ready to modify the chunk
	 */
	boolean isReady(ChunkView view);

	/**
	 * Modifies the ChunkView that will be send to players.
	 * This method might check and modify thousands blocks and thus must optimized to be as fast as possible.
	 * @param view The ChunkView to modify
	 * @param r The Random object that should be used by the modifier
	 */
	void modify(ChunkView view, Random r);
}
