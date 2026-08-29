package mapgen.core;

import java.util.BitSet;

/**
 * Блок карты 300x300. Пиксель (x, y) внутри блока -> мировой (worldX(x), worldY(y)).
 * Принадлежит одному потоку на всё время генерации (thread-confined), синхронизация не нужна.
 */
public final class Chunk {
    public final int cx, cy, size;
    private final Layer base, vegetation;
    private final BitSet vegetationBlocked;

    public Chunk(int cx, int cy, int size) {
        this.cx = cx;
        this.cy = cy;
        this.size = size;
        this.base = new Layer(size, size, 0x000000);
        this.vegetation = new Layer(size, size, 0x000000);
        this.vegetationBlocked = new BitSet(size * size);
    }

    public Layer base()       { return base; }
    public Layer vegetation() { return vegetation; }

    public int worldX(int x) { return cx * size + x; }
    public int worldY(int y) { return cy * size + y; }

    public void blockVegetation(int x, int y)        { vegetationBlocked.set(y * size + x); }
    public boolean isVegetationBlocked(int x, int y) { return vegetationBlocked.get(y * size + x); }
}
