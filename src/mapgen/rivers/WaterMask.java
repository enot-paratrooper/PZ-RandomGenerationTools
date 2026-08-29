package mapgen.rivers;

import mapgen.util.LongHashSet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Индекс водной поверхности: тайл 64x64 -&gt; список примитивов (диски узлов и капсулы векторов).
 * Попиксельная маска НЕ хранится, она собирается лениво в {@link View} и там же кэшируется.
 *
 * <p>Жизненный цикл: маска наполняется в фазе трассировки ({@link #add}), затем {@link #freeze()},
 * после чего она неизменяема и {@link #view()} можно раздать по одному на поток. Все мутабельные
 * данные обхода (LRU-кэш собранных тайлов, кэш последнего тайла) живут в View, а не в маске —
 * именно это позволяет читать её из многих потоков без синхронизации.
 *
 * <p>Во время трассировки маска тоже читается, но пишется только между реками (целая река
 * растеризуется разом), поэтому View сбрасывает кэш по счётчику версий — дёшево и корректно.
 */
public final class WaterMask {
    public static final int TILE_BITS = 6;
    public static final int TILE = 1 << TILE_BITS;          // 64x64 px, строка = один long
    /** Потолок LRU-кэша собранных тайлов НА ПОТОК. Обход блока построчный, локальность высокая. */
    public static final int MAX_CACHED_TILES = 768;         // 768 * 512 B = 384 КБ на поток

    /** Примитивы одного тайла: диски {x,y,r} и капсулы {x0,y0,x1,y1,r}. */
    private static final class Tile {
        int[] disks = new int[12];
        int dn;
        int[] caps;
        int cn;

        void addDisk(int x, int y, int r) {
            if (dn + 3 > disks.length) disks = Arrays.copyOf(disks, disks.length << 1);
            disks[dn++] = x; disks[dn++] = y; disks[dn++] = r;
        }

        void addCapsule(int x0, int y0, int x1, int y1, int r) {
            if (caps == null) caps = new int[10];
            if (cn + 5 > caps.length) caps = Arrays.copyOf(caps, caps.length << 1);
            caps[cn++] = x0; caps[cn++] = y0; caps[cn++] = x1; caps[cn++] = y1; caps[cn++] = r;
        }
    }

    private final Map<Long, Tile> tiles = new HashMap<>();
    private volatile int version;
    private volatile boolean frozen;

    public static long tileKey(int tx, int ty) { return ((long) tx << 32) | (ty & 0xFFFFFFFFL); }

    public boolean isFrozen() { return frozen; }
    public int tileCount()    { return tiles.size(); }

    /** Запрещает дальнейшие изменения; после этого view() потокобезопасен. */
    public void freeze() { frozen = true; }

    /** Добавляет геометрию реки в индекс. Только до freeze(). */
    public void add(River r) {
        if (frozen) throw new IllegalStateException("WaterMask заморожена");
        if (r.simplified()) {
            int[] v = r.vectorsRaw();
            for (int i = 0; i < v.length; i += 5)
                registerCapsule(v[i], v[i + 1], v[i + 2], v[i + 3], (v[i + 4] - 1) >> 1);
        } else {
            int[] n = r.nodesRaw();
            for (int i = 0; i < n.length; i += 3)
                registerDisk(n[i], n[i + 1], (n[i + 2] - 1) >> 1);
        }
        if (r.lakeRadius() > 0 && !r.simplified()) {
            int last = r.nodeCount() - 1;
            registerDisk(r.nodeX(last), r.nodeY(last), r.lakeRadius());
        }
        version++;      // View сбросит кэш собранных тайлов
    }

    /** Переносит содержимое другой маски (оверлей воркера) в эту. Порядок не влияет на результат. */
    public void absorb(WaterMask other) {
        if (frozen) throw new IllegalStateException("WaterMask заморожена");
        for (Map.Entry<Long, Tile> e : other.tiles.entrySet()) {
            Tile src = e.getValue();
            Tile dst = tiles.computeIfAbsent(e.getKey(), k -> new Tile());
            for (int i = 0; i < src.dn; i += 3) dst.addDisk(src.disks[i], src.disks[i + 1], src.disks[i + 2]);
            for (int i = 0; i < src.cn; i += 5)
                dst.addCapsule(src.caps[i], src.caps[i + 1], src.caps[i + 2], src.caps[i + 3], src.caps[i + 4]);
        }
        version++;
    }

    private void registerDisk(int x, int y, int radius) {
        int t0x = (x - radius) >> TILE_BITS, t1x = (x + radius) >> TILE_BITS;
        int t0y = (y - radius) >> TILE_BITS, t1y = (y + radius) >> TILE_BITS;
        for (int ty = t0y; ty <= t1y; ty++)
            for (int tx = t0x; tx <= t1x; tx++)
                tiles.computeIfAbsent(tileKey(tx, ty), k -> new Tile()).addDisk(x, y, radius);
    }

    /**
     * Капсула регистрируется только в тайлах, которых реально касается: идём вдоль отрезка
     * с шагом TILE/2 и помечаем тайлы под диском. Регистрация по габаритному прямоугольнику
     * зацепила бы для диагонали вдвое больше пустых тайлов.
     */
    private void registerCapsule(int x0, int y0, int x1, int y1, int radius) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        int steps = (int) Math.ceil(len / (TILE / 2.0));
        LongHashSet seen = new LongHashSet(64);
        for (int s = 0; s <= steps; s++) {
            double t = steps == 0 ? 0 : (double) s / steps;
            int px = (int) Math.round(x0 + dx * t), py = (int) Math.round(y0 + dy * t);
            int t0x = (px - radius) >> TILE_BITS, t1x = (px + radius) >> TILE_BITS;
            int t0y = (py - radius) >> TILE_BITS, t1y = (py + radius) >> TILE_BITS;
            for (int ty = t0y; ty <= t1y; ty++)
                for (int tx = t0x; tx <= t1x; tx++) {
                    long k = tileKey(tx, ty);
                    if (seen.add(k))
                        tiles.computeIfAbsent(k, kk -> new Tile()).addCapsule(x0, y0, x1, y1, radius);
                }
        }
    }

    /** Новый обходчик. Один на поток: внутри мутабельные кэши. */
    public View view() { return new View(); }

    /**
     * Потоко-локальное окно чтения маски. Не разделять между потоками.
     */
    public final class View {
        private final LinkedHashMap<Long, long[]> cache =
                new LinkedHashMap<>(256, 0.75f, true) {
                    @Override protected boolean removeEldestEntry(Map.Entry<Long, long[]> e) {
                        return size() > MAX_CACHED_TILES;
                    }
                };
        private long lastKey = Long.MIN_VALUE;
        private long[] lastMask;
        private int seenVersion = -1;

        private View() {}

        public boolean isWater(int wx, int wy) {
            int v = version;
            if (v != seenVersion) {                 // маска изменилась (только в фазе трассировки)
                cache.clear();
                lastKey = Long.MIN_VALUE;
                lastMask = null;
                seenVersion = v;
            }
            int tx = wx >> TILE_BITS, ty = wy >> TILE_BITS;
            long tk = tileKey(tx, ty);
            long[] m;
            if (tk == lastKey && lastMask != null) {          // построчный обход не выходит из тайла
                m = lastMask;
            } else {
                m = cache.get(tk);
                if (m == null) m = build(tx, ty, tk);
                lastKey = tk;
                lastMask = m;
            }
            return (m[wy & (TILE - 1)] >>> (wx & (TILE - 1)) & 1L) != 0;
        }

        private long[] build(int tx, int ty, long tk) {
            long[] m = new long[TILE];
            Tile t = tiles.get(tk);
            if (t != null) {
                int ox = tx << TILE_BITS, oy = ty << TILE_BITS;
                for (int i = 0; i < t.dn; i += 3)
                    disk(m, ox, oy, t.disks[i], t.disks[i + 1], t.disks[i + 2]);
                for (int i = 0; i < t.cn; i += 5)
                    capsule(m, ox, oy, t.caps[i], t.caps[i + 1], t.caps[i + 2], t.caps[i + 3], t.caps[i + 4]);
            }
            cache.put(tk, m);
            return m;
        }
    }

    // ------------------------------------------------------------------ заливка примитивов

    private static void disk(long[] m, int ox, int oy, int cx, int cy, int r) {
        int y0 = Math.max(oy, cy - r), y1 = Math.min(oy + TILE - 1, cy + r);
        for (int y = y0; y <= y1; y++) {
            int dy = y - cy;
            int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
            int x0 = Math.max(ox, cx - half), x1 = Math.min(ox + TILE - 1, cx + half);
            if (x0 > x1) continue;
            int a = x0 - ox, b = x1 - ox;
            m[y - oy] |= (b - a == TILE - 1) ? -1L : (((1L << (b - a + 1)) - 1) << a);
        }
    }

    /**
     * Капсула = объединение дисков вдоль отрезка. Отрезок предварительно обрезается по
     * габаритам тайла, расширенным на r, иначе длинный вектор пришлось бы обходить целиком
     * для каждого из десятков тайлов, которые он пересекает.
     */
    private static void capsule(long[] m, int ox, int oy, int x0, int y0, int x1, int y1, int r) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        double lo = 0, hi = 1;
        if (len > 0) {
            double[] range = clip(x0, y0, dx, dy, ox - r, oy - r, ox + TILE - 1 + r, oy + TILE - 1 + r);
            if (range == null) return;
            lo = range[0];
            hi = range[1];
        }
        double step = Math.max(1.0, r * 0.5);                 // перекрытие соседних дисков
        int n = len > 0 ? (int) Math.ceil(len * (hi - lo) / step) : 0;
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? lo : lo + (hi - lo) * i / n;
            disk(m, ox, oy, (int) Math.round(x0 + dx * t), (int) Math.round(y0 + dy * t), r);
        }
    }

    /** Метод слэбов: диапазон параметра t, на котором точка отрезка лежит в прямоугольнике. */
    private static double[] clip(double px, double py, double dx, double dy,
                                 double bx0, double by0, double bx1, double by1) {
        double lo = 0, hi = 1;
        double[][] axes = {{dx, px, bx0, bx1}, {dy, py, by0, by1}};
        for (double[] a : axes) {
            if (Math.abs(a[0]) < 1e-9) {
                if (a[1] < a[2] || a[1] > a[3]) return null;
            } else {
                double t0 = (a[2] - a[1]) / a[0], t1 = (a[3] - a[1]) / a[0];
                if (t0 > t1) { double s = t0; t0 = t1; t1 = s; }
                lo = Math.max(lo, t0);
                hi = Math.min(hi, t1);
                if (lo > hi) return null;
            }
        }
        return new double[]{lo, hi};
    }
}
