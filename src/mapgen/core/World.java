package mapgen.core;

import mapgen.colors.Palette;
import mapgen.rivers.RiverNetwork;

import java.util.Random;

/**
 * Мир — бесконечная плоскость в мировых пиксельных координатах. Высота, влажность и вода
 * детерминированы относительно seed, поэтому любой блок можно сгенерировать в любой момент,
 * и он сойдётся с соседями по швам.
 */
public final class World {
    public static final int CHUNK_SIZE = 300;

    private final long seed;
    private final Palette palette;
    private final WorldState state;
    private final NoiseField heightField, drainageField, meanderField, moistureField, patchField, forestField, bushField;
    /** Доля крупномасштабного уклона в высоте: 0 — чистый Perlin, 1 — только "водоразделы/долины". */
    private static final float DRAINAGE_WEIGHT = 0.38f;
    private final RiverNetwork rivers;
    private final double seaLevel, rockLevel;

    public World(WorldState state, Palette palette, double seaLevel, double rockLevel) {
        this.seed = state.seed;
        this.state = state;
        this.palette = palette;
        this.seaLevel = seaLevel;
        this.rockLevel = rockLevel;
        this.heightField   = new NoiseField(seed,        220, 6, 1.3);
        this.drainageField = new NoiseField(seed + 5,   2200, 2, 1.0); // период ~7 блоков: долины и хребты
        this.meanderField  = new NoiseField(seed + 6,   1200, 2, 1.0); // извилистость русел
        this.moistureField = new NoiseField(seed + 1337, 320, 4, 1.0);
        this.patchField    = new NoiseField(seed + 42,    18, 3, 1.0);
        this.forestField   = new NoiseField(seed + 777,   90, 5, 1.0);
        this.bushField     = new NoiseField(seed + 999,   36, 3, 1.0);
        this.rivers = new RiverNetwork(this, state);
    }

    public long seed()           { return seed; }
    public Palette palette()     { return palette; }
    public WorldState state()    { return state; }
    public RiverNetwork rivers() { return rivers; }
    public double seaLevel()     { return seaLevel; }
    public double rockLevel()    { return rockLevel; }

    /**
     * Высота = локальный рельеф + крупномасштабный уклон. Благодаря уклону у воды всегда есть
     * куда течь: низины drainage-поля становятся озёрами/морем, к ним стекают долины.
     */
    public float height(int wx, int wy) {
        return (1 - DRAINAGE_WEIGHT) * heightField.at(wx, wy) + DRAINAGE_WEIGHT * drainageField.at(wx, wy);
    }
    public float moisture(int wx, int wy) { return moistureField.at(wx, wy); }
    public float patch(int wx, int wy)    { return patchField.at(wx, wy); }
    public float forest(int wx, int wy)   { return forestField.at(wx, wy); }
    public float bush(int wx, int wy)     { return bushField.at(wx, wy); }

    /** Крупномасштабный уклон без мелких холмов. */
    public float drainage(int wx, int wy) { return drainageField.at(wx, wy); }

    /**
     * "Потенциал стока" для направления рек: гладкое поле, у которого нет локальных максимумов
     * на масштабе холмов, поэтому ветвь реки не застревает на ближайшем бугре.
     */
    public float flow(int wx, int wy) { return 0.8f * drainageField.at(wx, wy) + 0.2f * meanderField.at(wx, wy); }

    /** Природное озеро (ниже уровня моря) — только по шуму, без рек. */
    public boolean isLake(int wx, int wy) { return height(wx, wy) < seaLevel; }

    /** Любая вода: природное озеро либо русло/озеро реки. */
    public boolean isWater(int wx, int wy) { return isLake(wx, wy) || rivers.isWater(wx, wy); }

    /** Детерминированный Random для (salt, cx, cy). */
    public Random random(String salt, int cx, int cy) {
        long h = seed ^ (salt.hashCode() * 0x9E3779B97F4A7C15L) ^ (cx * 73856093L) ^ (cy * 19349663L);
        return new Random(h * 0xBF58476D1CE4E5B9L);
    }
}
