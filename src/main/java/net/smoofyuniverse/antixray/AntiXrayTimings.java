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

package net.smoofyuniverse.antixray;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;

public class AntiXrayTimings {
	public static final Timing OBFUSCATION = Timings.of(AntiXray.get(), "Obfuscation");
	public static final Timing PREOBFUSCATION = Timings.of(AntiXray.get(), "Preobfuscation");
	public static final Timing DEOBFUSCATION = Timings.of(AntiXray.get(), "Deobfuscation");
	public static final Timing REOBFUSCATION = Timings.of(AntiXray.get(), "Reobfuscation");
	public static final Timing SENDING_CHANGES = Timings.of(AntiXray.get(), "Sending Changes");
	public static final Timing WRITING_CACHE = Timings.of(AntiXray.get(), "Writing Cache");
	public static final Timing READING_CACHE = Timings.of(AntiXray.get(), "Reading Cache");
}
