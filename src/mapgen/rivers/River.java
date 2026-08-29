package mapgen.rivers;

import java.util.List;

/**
 * Речная система (дерево): устье (mouthX, mouthY) на берегу озера и все клетки русел {wx, wy, width}.
 * Ширина уже посчитана по числу притоков, поэтому для растеризации структура дерева не нужна.
 */
public record River(int mouthX, int mouthY, List<int[]> path, int lakeRadius) {
    public int sourceX() { return mouthX; }
    public int sourceY() { return mouthY; }
}
