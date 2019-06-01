/*
 * Copyright (c) 2018-2019 Hugo Dupanloup (Yeregorix)
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

package net.smoofyuniverse.mirage.config.world;

import com.google.common.collect.ImmutableSet;
import net.smoofyuniverse.mirage.util.collection.BlockSet;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.block.BlockState;

import java.util.Collection;
import java.util.Set;

@ConfigSerializable
public class PreobfuscationConfig {
	@Setting(value = "Enabled", comment = "Enable or disable preobfuscation in this world")
	public boolean enabled = false;
	@Setting(value = "Blocks", comment = "Blocks that will be hidden by the modifier")
	public BlockSet blocks;
	@Setting(value = "Replacement", comment = "The block used to replace hidden blocks")
	public BlockState replacement;

	public Immutable toImmutable() {
		return new Immutable(this.enabled, this.blocks.getAll(), this.replacement);
	}

	public static class Immutable {
		public final boolean enabled;
		public final Set<BlockState> blocks;
		public final BlockState replacement;

		public Immutable(boolean enabled, Collection<BlockState> blocks, BlockState replacement) {
			this.enabled = enabled;
			this.blocks = ImmutableSet.copyOf(blocks);
			this.replacement = replacement;
		}
	}
}
