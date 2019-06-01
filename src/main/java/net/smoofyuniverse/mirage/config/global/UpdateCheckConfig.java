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

package net.smoofyuniverse.mirage.config.global;

import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

@ConfigSerializable
public class UpdateCheckConfig {
	@Setting(value = "Enabled", comment = "Enable or disable automatic update checking")
	public boolean enabled = true;
	@Setting(value = "RepetitionInterval", comment = "Interval in hours between update checking repetitions, 0 to disable")
	public int repetitionInterval = 12;
	@Setting(value = "ConsoleDelay", comment = "Delay in ticks before sending a message to the console, between -1 and 100")
	public int consoleDelay = 20;
	@Setting(value = "PlayerDelay", comment = "Delay in ticks before sending a message after a player connection, between -1 and 100")
	public int playerDelay = 20;

	public Immutable toImmutable() {
		return new Immutable(this.enabled, this.repetitionInterval, this.consoleDelay, this.playerDelay);
	}

	public static class Immutable {
		public final boolean enabled;
		public final int repetitionInterval, consoleDelay, playerDelay;

		public Immutable(boolean enabled, int repetitionInterval, int consoleDelay, int playerDelay) {
			this.enabled = enabled;
			this.repetitionInterval = repetitionInterval;
			this.consoleDelay = consoleDelay;
			this.playerDelay = playerDelay;
		}
	}
}
