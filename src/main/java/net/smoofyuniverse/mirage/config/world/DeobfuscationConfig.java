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

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public class DeobfuscationConfig {

	@Comment("Radius to deobfuscate on natural block update, between 1 and 4")
	@Setting("NaturalRadius")
	public int naturalRadius = 1;

	@Comment("Radius to deobfuscate on player block update, between 1 and 4")
	@Setting("PlayerRadius")
	public int playerRadius = 2;

	public Resolved resolve() {
		return new Resolved(this.naturalRadius, this.playerRadius);
	}

	public static class Resolved {
		public final int naturalRadius, playerRadius;

		public Resolved(int naturalRadius, int playerRadius) {
			this.naturalRadius = naturalRadius;
			this.playerRadius = playerRadius;
		}
	}
}
