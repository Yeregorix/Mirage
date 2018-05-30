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

package net.smoofyuniverse.mirage.ore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.smoofyuniverse.mirage.Mirage;
import net.smoofyuniverse.mirage.ore.adapter.InstantAdapter;
import net.smoofyuniverse.mirage.ore.object.DependencyInfo;
import net.smoofyuniverse.mirage.ore.object.VersionInfo;

import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class OreAPI {
	public static final URL URL_BASE = getUrl();
	public static final Gson GSON = new GsonBuilder().registerTypeAdapter(Instant.class, new InstantAdapter()).create();

	private static URL getUrl() {
		try {
			return new URL("https://ore.spongepowered.org/api/v1/");
		} catch (MalformedURLException e) {
			Mirage.LOGGER.info("Failed to load object url", e);
			return null;
		}
	}

	public static List<VersionInfo> getProjectVersions(String projectId, String... channels) throws Exception {
		return getProjectVersions(projectId, 0, 10, channels);
	}

	public static List<VersionInfo> getProjectVersions(String projectId, int offset, int limit, String... channels) throws Exception {
		String suffix = "projects/" + projectId + "/versions?offset=" + offset + "&limit=" + limit;
		if (channels.length != 0)
			suffix += "&channels=" + String.join(",", channels);

		return GSON.fromJson(new InputStreamReader(getUrl(suffix).openStream()), new TypeToken<List<VersionInfo>>() {}.getType());
	}

	public static URL getUrl(String suffix) throws MalformedURLException {
		return new URL(URL_BASE.getProtocol(), URL_BASE.getHost(), URL_BASE.getPort(), URL_BASE.getFile() + suffix);
	}

	public static Optional<String> getLatestVersion(List<VersionInfo> versions, String... spongeApiVersions) {
		String version = null;
		Instant date = null;

		for (VersionInfo info : versions) {
			if (date == null || info.createdAt.isAfter(date)) {
				String spongeApiVersion = null;
				for (DependencyInfo d : info.dependencies) {
					if (d.pluginId.equals("spongeapi")) {
						spongeApiVersion = d.version;
						break;
					}
				}

				if (spongeApiVersion != null) {
					boolean valid = false;
					for (String v : spongeApiVersions) {
						if (spongeApiVersion.startsWith(v)) {
							valid = true;
							break;
						}
					}

					if (valid) {
						version = info.name;
						date = info.createdAt;
					}
				}
			}
		}

		return Optional.ofNullable(version);
	}
}
