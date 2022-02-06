/*
 * Copyright (c) 2018-2022 Hugo Dupanloup (Yeregorix)
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

import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.world.WorldType;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public class MainConfig {

	@Comment("Enable or disable Mirage in this world")
	@Setting("Enabled")
	public boolean enabled = true;

	@Comment("Enable or disable caching fake chunks on disk")
	@Setting("Cache")
	public boolean cache = true;

	@Comment("Enable or disable dynamic obfuscation")
	@Setting("Dynamism")
	public boolean dynamism = true;

	@Comment("The world type used for automatic config generation")
	@Setting("WorldType")
	public ResourceKey worldType;

	@Setting("Deobfuscation")
	public DeobfuscationConfig deobf = new DeobfuscationConfig();

	public Resolved resolve(WorldType worldType) {
		return new Resolved(this.enabled, this.cache, this.dynamism, worldType, this.deobf.resolve());
	}

	public static class Resolved {
		public final boolean enabled, cache, dynamism;
		public final WorldType worldType;
		public final DeobfuscationConfig.Resolved deobf;

		public Resolved(boolean enabled, boolean cache, boolean dynamism, WorldType worldType, DeobfuscationConfig.Resolved deobf) {
			this.enabled = enabled;
			this.cache = cache;
			this.dynamism = dynamism;
			this.worldType = worldType;
			this.deobf = deobf;
		}

		public Resolved disable() {
			return this.enabled ? new Resolved(false, this.cache, this.dynamism, this.worldType, this.deobf) : this;
		}
	}
}
