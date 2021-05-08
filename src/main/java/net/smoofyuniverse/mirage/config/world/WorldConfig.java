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

package net.smoofyuniverse.mirage.config.world;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

@ConfigSerializable
public class WorldConfig {
	public static final TypeToken<WorldConfig> TOKEN = TypeToken.of(WorldConfig.class);

	@Setting(value = "Enabled", comment = "Enable or disable Mirage in this world")
	public boolean enabled = true;
	@Setting(value = "Cache", comment = "Enable or disable caching fake chunks on disk")
	public boolean cache = true;
	@Setting(value = "Dynamism", comment = "Enable or disable dynamic obfuscation")
	public boolean dynamism = true;
	@Setting(value = "Seed", comment = "The seed used by the modifier, shouldn't be modified")
	public long seed = 0;
	@Setting(value = "Deobfuscation")
	public DeobfuscationConfig deobf = new DeobfuscationConfig();

	public WorldConfig(boolean enabled) {
		this.enabled = enabled;
	}

	public WorldConfig() {}

	public Immutable toImmutable() {
		return new Immutable(this.enabled, this.cache, this.dynamism, this.seed, this.deobf.toImmutable());
	}

	public static class Immutable {
		public final boolean enabled, cache, dynamism;
		public final long seed;
		public final DeobfuscationConfig.Immutable deobf;

		public Immutable(boolean enabled, boolean cache, boolean dynamism, long seed, DeobfuscationConfig.Immutable deobf) {
			this.enabled = enabled;
			this.cache = cache;
			this.dynamism = dynamism;
			this.seed = seed;
			this.deobf = deobf;
		}
	}
}
