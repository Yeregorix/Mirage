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

package net.smoofyuniverse.mirage.config.serializer;

import com.google.common.reflect.TypeToken;
import net.smoofyuniverse.mirage.util.collection.BlockSet;
import net.smoofyuniverse.mirage.util.collection.BlockSet.SerializationPredicate;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer;

import java.util.ArrayList;
import java.util.List;

public class BlockSetSerializer implements TypeSerializer<BlockSet> {
	private static final TypeToken<String> STRING = TypeToken.of(String.class);

	public final SerializationPredicate predicate;

	public BlockSetSerializer(SerializationPredicate predicate) {
		this.predicate = predicate;
	}

	@Override
	public BlockSet deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException {
		try {
			BlockSet set = new BlockSet();
			set.deserialize(value.getList(STRING), false);
			return set;
		} catch (IllegalArgumentException e) {
			throw new ObjectMappingException(e.getMessage());
		}
	}

	@Override
	public void serialize(TypeToken<?> type, BlockSet set, ConfigurationNode value) throws ObjectMappingException {
		try {
			List<String> l = new ArrayList<>();
			set.serialize(l, this.predicate);
			value.setValue(l);
		} catch (IllegalArgumentException e) {
			throw new ObjectMappingException(e.getMessage());
		}
	}
}
