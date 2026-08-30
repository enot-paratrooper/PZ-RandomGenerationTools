package mapgen.core;

import mapgen.rivers.WaterMask;
import mapgen.towns.TownIndex;

/**
 * Всё изменяемое, что нужно генераторам, — по одному экземпляру на поток.
 *
 * <p>Держит окно {@link World#height} и {@link World#moisture} на блок с каймой в 1 px.
 * Без него проверка берегов в RiverGenerator вызывает height() восемь раз на пиксель, а каждый
 * вызов — два fBm (6 и 2 октавы). На блоке 300x300 это ~700 тыс. вычислений шума там, где
 * достаточно 91 тыс. Кайма нужна потому, что соседей смотрят и за границей блока.
 *
 * <p>Здесь же живёт кэш построенных городов ({@link TownIndex}): геометрия города детерминирована,
 * но её постройка стоит сотен прямоугольников, а один город покрывает десятки блоков.
 *
 * <p>Память: два float[302*302] = 730 КБ на поток, плюс LRU водной маски и до
 * {@link TownIndex#MAX_CACHED_TOWNS} городов.
 */
public final class GenContext {
    private final World world;
    private final WaterMask.View water;
    private final TownIndex towns;
    private final int size, stride;
    private final float[] height, moisture;
    private int ox, oy;                 // мировые координаты левого верхнего угла окна (с каймой)
    private boolean ready;

    public static final int MARGIN = 1;

    public GenContext(World world, WaterMask mask, int chunkSize) {
        if (!mask.isFrozen()) throw new IllegalStateException("маска должна быть заморожена до растеризации");
        this.world = world;
        this.water = mask.view();
        this.towns = new TownIndex(world.townField());
        this.size = chunkSize;
        this.stride = chunkSize + 2 * MARGIN;
        this.height = new float[stride * stride];
        this.moisture = new float[stride * stride];
    }

    public World world() { return world; }
    public TownIndex towns() { return towns; }

    /** Пересчитывает окно полей под новый блок. Вызывается конвейером перед первым генератором. */
    public void beginChunk(Chunk c) {
        if (c.size != size) throw new IllegalArgumentException("размер блока не совпадает с контекстом");
        ox = c.cx * size - MARGIN;
        oy = c.cy * size - MARGIN;
        for (int j = 0; j < stride; j++) {
            int row = j * stride, wy = oy + j;
            for (int i = 0; i < stride; i++) {
                int wx = ox + i;
                height[row + i] = world.height(wx, wy);
                moisture[row + i] = world.moisture(wx, wy);
            }
        }
        ready = true;
    }

    private int idx(int wx, int wy) {
        int i = wx - ox, j = wy - oy;
        return (ready && i >= 0 && j >= 0 && i < stride && j < stride) ? j * stride + i : -1;
    }

    public float height(int wx, int wy) {
        int k = idx(wx, wy);
        return k >= 0 ? height[k] : world.height(wx, wy);
    }

    public float moisture(int wx, int wy) {
        int k = idx(wx, wy);
        return k >= 0 ? moisture[k] : world.moisture(wx, wy);
    }

    public boolean isLake(int wx, int wy)      { return height(wx, wy) < world.seaLevel(); }
    public boolean isRiverWater(int wx, int wy){ return water.isWater(wx, wy); }
    /** Любая вода: природное озеро либо русло реки. */
    public boolean isWater(int wx, int wy)     { return isLake(wx, wy) || water.isWater(wx, wy); }
}
