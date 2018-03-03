package net.smoofyuniverse.antixray.impl.network;

public interface ChunkChangeListener {
	void addChange(int x, int y, int z);

	void sendChanges();

	void clearChanges();
}
