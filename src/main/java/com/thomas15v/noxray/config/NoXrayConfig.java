/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thomas Vanmellaerts
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

package com.thomas15v.noxray.config;

import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

@ConfigSerializable
public class NoXrayConfig {
	private static final String USE_ORE_DICT_KEY = "UseOreDict";
	private static final String USE_ORE_DICT_COMMENT = "In case forge is detected, use the forgedict to add extra ores";
	@Setting(value = USE_ORE_DICT_KEY, comment = USE_ORE_DICT_COMMENT)
	private boolean useOreDict = true;

	private static final String USE_CAVE = "cave.enabled";
	private static final String USE_CAVE_COMMENT = "Enable ore hiding in dark caves";
	@Setting(value = USE_CAVE, comment = USE_CAVE_COMMENT)
	private boolean caveEnabled = false;

	private static final String CAVE_ENABLE_RANGE = "cave.enablerange";
	private static final String USE_CAVE_ENABLE_COMMENT = "Allow ores to be visible in the dark without potions when a player is close";
	@Setting(value = CAVE_ENABLE_RANGE, comment = USE_CAVE_ENABLE_COMMENT)
	private boolean enableRange = false;

	private static final String CAVE_RANGE = "cave.range";
	private static final String CAVE_RANGE_COMMENT = "The distance a player has to be from the ore before it comes visible in the dark (-1: never)";
	@Setting(value = CAVE_RANGE, comment = CAVE_RANGE_COMMENT)
	private int caveRange = 16;


	public boolean isUseOreDict() {
		return useOreDict;
	}

	public boolean isCaveEnabled() {
		return caveEnabled;
	}

	public int getCaveRange() {
		return caveRange;
	}
}
