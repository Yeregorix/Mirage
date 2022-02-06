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

package net.smoofyuniverse.mirage.util;

import net.smoofyuniverse.mirage.Mirage;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.registry.Registry;
import org.spongepowered.api.registry.RegistryTypes;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class BlockResolver {
	private final Registry<BlockType> blockTypeRegistry = RegistryTypes.BLOCK_TYPE.get();
	private final BlockState.Builder blockStateBuilder = BlockState.builder();
	private final Set<String> unknownKeys = new HashSet<>(), invalidPatterns = new HashSet<>();

	public void resolve(String input, Consumer consumer) {
		boolean negate = input.startsWith("-");
		if (negate)
			input = input.substring(1);

		if (input.startsWith("regex!")) {
			input = input.substring(6);

			Pattern pattern;
			try {
				pattern = Pattern.compile(input, Pattern.CASE_INSENSITIVE);
			} catch (PatternSyntaxException e) {
				invalidPatterns.add(input);
				return;
			}

			blockTypeRegistry.stream()
					.flatMap(type -> type.validStates().stream())
					.filter(state -> pattern.matcher(state.toString()).matches())
					.forEach(state -> consumer.accept(state, negate));
			return;
		}

		try {
			Optional<BlockType> type = blockTypeRegistry.findValue(ResourceKey.resolve(input));
			if (type.isPresent()) {
				consumer.accept(type.get(), negate);
				return;
			}
		} catch (Exception ignored) {
		}

		try {
			BlockState state = blockStateBuilder.reset().fromString(input).build();
			consumer.accept(state, negate);
			return;
		} catch (Exception ignored) {
		}

		unknownKeys.add(input);
	}

	public void flushErrors() {
		if (!this.unknownKeys.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			sb.append("Unknown block states:");
			for (String key : this.unknownKeys)
				sb.append(' ').append(key);

			this.unknownKeys.clear();
			Mirage.LOGGER.warn(sb.toString());
		}

		if (!this.invalidPatterns.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			sb.append("Invalid block states patterns:");
			for (String pattern : this.invalidPatterns)
				sb.append(' ').append(pattern);

			this.invalidPatterns.clear();
			Mirage.LOGGER.warn(sb.toString());
		}
	}

	interface Consumer {
		default void accept(BlockType type, boolean negate) {
			for (BlockState state : type.validStates())
				accept(state, negate);
		}

		void accept(BlockState state, boolean negate);
	}
}
