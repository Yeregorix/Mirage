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
