/*
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

package net.smoofyuniverse.mirage.modifier;

import com.flowpowered.math.vector.Vector3i;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.api.cache.Signature.Builder;
import net.smoofyuniverse.mirage.api.modifier.ChunkModifier;
import net.smoofyuniverse.mirage.api.volume.BlockView;
import net.smoofyuniverse.mirage.api.volume.ChunkView;
import ninja.leaping.configurate.ConfigurationNode;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.Random;

/**
 * This modifier does not modify anything.
 */
public class EmptyModifier extends ChunkModifier {

	public EmptyModifier() {
		super(Mirage.get(), "Empty");
	}

	@Override
	public boolean shouldCache() {
		return false;
	}

	@Override
	public Object loadConfiguration(ConfigurationNode node, WorldProperties world, String preset) {
		return new Object();
	}

	@Override
	public void appendSignature(Builder builder, Object config) {}

	@Override
	public boolean isReady(ChunkView view, Object config) {
		return true;
	}

	@Override
	public void modify(BlockView view, Vector3i min, Vector3i max, Random r, Object config) {}
}
