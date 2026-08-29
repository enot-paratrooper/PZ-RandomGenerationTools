package mapgen.rivers;

import mapgen.core.World;
import mapgen.core.WorldState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Реки растут от устьев к истокам. В каждом регионе (REGION_CHUNKS^2 блоков) выбираются точки
 * на берегу природных озёр; от них вверх по склону растёт дерево: направление ветви выбирается
 * по гладкому потенциалу стока World.flow() (крупный уклон + извилистость), а не по пиксельной
 * высоте — иначе ветвь глохнет на первом же холме. Ветви с некоторой вероятностью раздваиваются,
 * останавливаются на хребтах, у чужих рек и при длительном спуске. Ширина русла = f(число клеток
 * выше по течению), поэтому ствол у устья широкий (до MAX_WIDTH px), а истоки — ручьи.
 * Всё детерминировано от seed и порядка регионов; готовые системы хранятся в WorldState.
 *
 * Вода НЕ хранится попиксельно: в памяти лежат только осевые линии {x, y, width}, разложенные
 * по тайлам TILE×TILE, а битовая маска тайла строится лениво и кэшируется (LRU). Поэтому рост
 * ширины с 7 до 50 px не увеличивает потребление памяти — см. isWater()/buildMask().
 */
public final class RiverNetwork {
    public static final int REGION_CHUNKS = 4;
    public static final int MOUTHS_PER_REGION = 2;
    public static final int MAX_BRANCH_LENGTH = 5000;   // длина одной ветви от устья, px
    public static final int MAX_NODES = 15000;           // суммарный размер дерева
    public static final int MAX_ACTIVE_TIPS = 4;
    public static final double BRANCH_PROB = 0.001;
    public static final int MIN_LENGTH_BEFORE_BRANCH = 80;
    public static final double RIDGE_DRAINAGE = 0.70;   // водораздел: выше — ветвь заканчивается (исток)
    public static final int MAX_DOWNHILL_STEPS = 150;   // сколько подряд шагов "вниз" по потенциалу терпим
    public static final int LOOKAHEAD = 16;              // на сколько px вперёд смотрим при выборе направления

    // --- ширина русла ---
    /** Максимальная толщина русла, px (ствол у устья крупной системы). */
    public static final int MAX_WIDTH = 50;
    /** width = WIDTH_K * sqrt(клеток выше по течению): 500 -> 10 px, 3000 -> 25 px, 14000+ -> 50 px. */
    public static final double WIDTH_K = 0.42;

    // --- геометрия ветвления ---
    /** Курс, который новая ветвь держит: поворот направления ствола на этот угол, град. */
    public static final double BRANCH_ANGLE_DEG = 62;
    /** Сколько шагов ветвь удерживает свой курс, прежде чем снова слушать только потенциал стока. */
    public static final int BRANCH_BIAS_STEPS = 150;
    /** Полураствор конуса, в котором ветви разрешено выбирать шаг, пока держит курс, град. */
    public static final double BRANCH_CONE_DEG = 45;
    /** Минимальный угол между первым шагом ветви и первым шагом ствола, град. */
    public static final double BRANCH_MIN_ANGLE_DEG = 45;

    // --- водная маска ---
    private static final int TILE_BITS = 6;
    private static final int TILE = 1 << TILE_BITS;                 // 64 x 64 px, одна строка = один long
    private static final int MAX_CACHED_TILES = 4096;               // 4096 * 512 B = 2 МБ потолок кэша

    private static final float INV_SQRT2 = (float) (1 / Math.sqrt(2));
    private static final float BRANCH_CONE_COS = (float) Math.cos(Math.toRadians(BRANCH_CONE_DEG));
    private static final float BRANCH_MIN_COS = (float) Math.cos(Math.toRadians(BRANCH_MIN_ANGLE_DEG));
    private static final float BRANCH_ROT_COS = (float) Math.cos(Math.toRadians(BRANCH_ANGLE_DEG));
    private static final float BRANCH_ROT_SIN = (float) Math.sin(Math.toRadians(BRANCH_ANGLE_DEG));

    private final World world;
    private final WorldState state;
    private final int regionSize;
    private final int reach;

    /** тайл -> осевые узлы {x, y, radius}; память O(число узлов), а не O(площадь воды). */
    private final Map<Long, IntBag> tileNodes = new HashMap<>();
    /** тайл -> битовая маска воды 64x64 (по одному long на строку), LRU. */
    private final LinkedHashMap<Long, long[]> maskCache =
            new LinkedHashMap<>(512, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, long[]> eldest) {
                    return size() > MAX_CACHED_TILES;
                }
            };
    private long lastKey = Long.MIN_VALUE;
    private long[] lastMask;

    public RiverNetwork(World world, WorldState state) {
        this.world = world;
        this.state = state;
        this.regionSize = REGION_CHUNKS * World.CHUNK_SIZE;
        this.reach = (int) Math.ceil((double) MAX_BRANCH_LENGTH / regionSize) + 1;
        for (River r : state.rivers) rasterize(r);
    }

    public boolean isWater(int wx, int wy) {
        int tx = wx >> TILE_BITS, ty = wy >> TILE_BITS;
        long tk = WorldState.key(tx, ty);
        long[] m;
        if (tk == lastKey && lastMask != null) {                        // построчный обход не выходит из тайла
            m = lastMask;
        } else {
            m = maskCache.get(tk);
            if (m == null) m = buildMask(tx, ty, tk);
            lastKey = tk; lastMask = m;
        }
        return (m[wy & (TILE - 1)] >>> (wx & (TILE - 1)) & 1L) != 0;
    }

    public List<River> rivers() { return state.rivers; }

    public void ensureTracedAround(int cx, int cy) {
        int rx = Math.floorDiv(cx, REGION_CHUNKS), ry = Math.floorDiv(cy, REGION_CHUNKS);
        for (int y = ry - reach; y <= ry + reach; y++)
            for (int x = rx - reach; x <= rx + reach; x++)
                traceRegion(x, y);
    }

    private void traceRegion(int rx, int ry) {
        long key = WorldState.key(rx, ry);
        if (state.tracedRegions.contains(key)) return;
        state.tracedRegions.add(key);

        Random rnd = new Random(world.seed() ^ (rx * 0x9E3779B97F4A7C15L) ^ (ry * 0xC2B2AE3D27D4EB4FL));
        int ox = rx * regionSize, oy = ry * regionSize;
        for (int i = 0; i < MOUTHS_PER_REGION; i++) {
            for (int attempt = 0; attempt < 80; attempt++) {
                int[] mouth = findMouth(ox + rnd.nextInt(regionSize), oy + rnd.nextInt(regionSize), rnd);
                if (mouth == null) continue;
                River r = grow(mouth[0], mouth[1], rnd);
                if (r.path().size() < 40) continue;       // не вырос — пробуем другое устье
                state.rivers.add(r);
                rasterize(r);
                break;
            }
        }
    }

    /** Из случайной точки озера идём к самому высокому соседу, пока не выйдем на берег. */
    private int[] findMouth(int x, int y, Random rnd) {
        if (!world.isLake(x, y)) return null;
        for (int step = 0; step < 400; step++) {
            int bx = x, by = y;
            float best = -1;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    float v = world.height(x + dx, y + dy) + (float) (rnd.nextDouble() * 0.002);
                    if (v > best) { best = v; bx = x + dx; by = y + dy; }
                }
            x = bx; y = by;
            if (!world.isLake(x, y)) return isWater(x, y) ? null : new int[]{x, y};
        }
        return null;
    }

    /** Растущий конец ветви. dirX/dirY — единичный курс, который ветвь держит biasLeft шагов. */
    private static final class Tip {
        int x, y, node, length, downhill, biasLeft;
        float dirX, dirY;
        Tip(int x, int y, int node, int length) { this.x = x; this.y = y; this.node = node; this.length = length; }
    }

    private River grow(int mx, int my, Random rnd) {
        List<int[]> nodes = new ArrayList<>();   // {x, y}
        List<Integer> parent = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Tip> tips = new ArrayDeque<>();

        // буферы кандидатов переиспользуются на всех шагах — без аллокаций в цикле роста
        int[] cdx = new int[8], cdy = new int[8];
        float[] cs = new float[8];

        nodes.add(new int[]{mx, my});
        parent.add(-1);
        visited.add(WorldState.key(mx, my));
        tips.add(new Tip(mx, my, 0, 0));

        while (!tips.isEmpty() && nodes.size() < MAX_NODES) {
            Tip t = tips.poll();
            if (t.length >= MAX_BRANCH_LENGTH) continue;
            if (world.drainage(t.x, t.y) > RIDGE_DRAINAGE) continue;      // дошли до водораздела — исток
            if (world.height(t.x, t.y) > world.rockLevel()) continue;     // не лезем в скалы
            float h = world.flow(t.x, t.y);

            int parentNode = parent.get(t.node);
            int px = parentNode >= 0 ? nodes.get(parentNode)[0] : Integer.MIN_VALUE;
            int py = parentNode >= 0 ? nodes.get(parentNode)[1] : Integer.MIN_VALUE;

            // допустимые соседи и их оценка по гладкому потенциалу с просмотром вперёд + шум для извилистости
            int cnt = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = t.x + dx, ny = t.y + dy;
                    if (!allowed(nx, ny, t.x, t.y, px, py, visited)) continue;
                    cdx[cnt] = dx; cdy[cnt] = dy;
                    cs[cnt] = world.flow(t.x + dx * LOOKAHEAD, t.y + dy * LOOKAHEAD)
                            + (float) (rnd.nextDouble() * 0.002);
                    cnt++;
                }
            }
            if (cnt == 0) continue;                                      // некуда расти

            // основной ход: пока ветвь держит курс, шаг ограничен конусом вокруг него
            int bi = pick(cnt, cs, cdx, cdy, t, true);
            if (bi < 0) { t.biasLeft = 0; bi = pick(cnt, cs, cdx, cdy, t, false); }
            float best = cs[bi];

            if (best < h) { if (++t.downhill > MAX_DOWNHILL_STEPS) continue; }
            else t.downhill = 0;

            int bx = t.x + cdx[bi], by = t.y + cdy[bi];
            float inv = (cdx[bi] != 0 && cdy[bi] != 0) ? INV_SQRT2 : 1f;
            float tux = cdx[bi] * inv, tuy = cdy[bi] * inv;              // единичное направление ствола

            // ветвление выбираем ДО добавления узла ствола: развилка вправе занять соседнюю клетку
            int si = -1;
            if (t.length > MIN_LENGTH_BEFORE_BRANCH && tips.size() < MAX_ACTIVE_TIPS
                    && rnd.nextDouble() < BRANCH_PROB) {
                float sBest = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < cnt; i++) {
                    if (i == bi || cs[i] < h) continue;                  // ветвь должна уходить вверх по склону
                    float iv = (cdx[i] != 0 && cdy[i] != 0) ? INV_SQRT2 : 1f;
                    float ux = cdx[i] * iv, uy = cdy[i] * iv;
                    if (ux * tux + uy * tuy > BRANCH_MIN_COS) continue;  // слишком острый угол к стволу
                    if (cs[i] > sBest) { sBest = cs[i]; si = i; }
                }
            }

            int idx = addNode(nodes, parent, visited, bx, by, t.node);
            Tip next = new Tip(bx, by, idx, t.length + 1);
            next.downhill = t.downhill;                                  // счётчик спуска живёт вдоль ветви
            if (t.biasLeft > 0) { next.biasLeft = t.biasLeft - 1; next.dirX = t.dirX; next.dirY = t.dirY; }
            tips.add(next);

            if (si >= 0) {
                int sx = t.x + cdx[si], sy = t.y + cdy[si];
                int bidx = addNode(nodes, parent, visited, sx, sy, t.node);
                Tip br = new Tip(sx, sy, bidx, t.length + 1);
                // курс ветви = направление ствола, повёрнутое на BRANCH_ANGLE_DEG в её сторону
                float iv = (cdx[si] != 0 && cdy[si] != 0) ? INV_SQRT2 : 1f;
                float sign = (tux * (cdy[si] * iv) - tuy * (cdx[si] * iv)) >= 0 ? 1f : -1f;
                br.dirX = tux * BRANCH_ROT_COS - tuy * BRANCH_ROT_SIN * sign;
                br.dirY = tux * BRANCH_ROT_SIN * sign + tuy * BRANCH_ROT_COS;
                br.biasLeft = BRANCH_BIAS_STEPS;
                tips.add(br);
            }
        }

        // ширина по размеру поддерева (дети всегда создаются после родителей -> обратный проход)
        int[] upstream = new int[nodes.size()];
        Arrays.fill(upstream, 1);
        for (int i = nodes.size() - 1; i > 0; i--) upstream[parent.get(i)] += upstream[i];

        List<int[]> path = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++)
            path.add(new int[]{nodes.get(i)[0], nodes.get(i)[1], widthPx(upstream[i])});
        return new River(mx, my, path, 0);
    }

    /** Лучший кандидат по потенциалу; при cone=true ветвь, удерживающая курс, ограничена конусом вокруг него. */
    private static int pick(int cnt, float[] cs, int[] cdx, int[] cdy, Tip t, boolean cone) {
        int bi = -1;
        float best = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < cnt; i++) {
            if (cone && t.biasLeft > 0) {
                float iv = (cdx[i] != 0 && cdy[i] != 0) ? INV_SQRT2 : 1f;
                if ((cdx[i] * t.dirX + cdy[i] * t.dirY) * iv < BRANCH_CONE_COS) continue;
            }
            if (cs[i] > best) { best = cs[i]; bi = i; }
        }
        return bi;
    }

    /** Толщина русла в px по числу клеток выше по течению (гидравлическая зависимость ~ sqrt расхода). */
    public static int widthPx(int upstream) {
        int w = (int) Math.round(WIDTH_K * Math.sqrt(upstream));
        return Math.max(1, Math.min(MAX_WIDTH, w));
    }

    /** Клетка допустима, если не вода, не посещена и не касается чужих клеток дерева (кроме текущей и её родителя). */
    private boolean allowed(int nx, int ny, int cx, int cy, int px, int py, Set<Long> visited) {
        if (world.isLake(nx, ny) || isWater(nx, ny)) return false;
        if (visited.contains(WorldState.key(nx, ny))) return false;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int ax = nx + dx, ay = ny + dy;
                if ((ax == cx && ay == cy) || (ax == px && ay == py)) continue;
                if (visited.contains(WorldState.key(ax, ay))) return false;
            }
        return true;
    }

    private static int addNode(List<int[]> nodes, List<Integer> parent, Set<Long> visited, int x, int y, int par) {
        nodes.add(new int[]{x, y});
        parent.add(par);
        visited.add(WorldState.key(x, y));
        return nodes.size() - 1;
    }

    // ------------------------------------------------------------------ водная маска

    /** Компактный список узлов тайла: тройки {x, y, radius} без обёрток. */
    private static final class IntBag {
        int[] a = new int[12];
        int n;
        void add(int x, int y, int r) {
            if (n + 3 > a.length) a = Arrays.copyOf(a, a.length << 1);
            a[n++] = x; a[n++] = y; a[n++] = r;
        }
    }

    private void rasterize(River r) {
        for (int[] p : r.path()) register(p[0], p[1], (p[2] - 1) >> 1);
        if (r.lakeRadius() > 0) {
            int[] end = r.path().get(r.path().size() - 1);
            register(end[0], end[1], r.lakeRadius());
        }
    }

    /** Регистрируем узел во всех тайлах, которых касается его диск (обычно 1, максимум 4). */
    private void register(int x, int y, int radius) {
        int t0x = (x - radius) >> TILE_BITS, t1x = (x + radius) >> TILE_BITS;
        int t0y = (y - radius) >> TILE_BITS, t1y = (y + radius) >> TILE_BITS;
        for (int ty = t0y; ty <= t1y; ty++)
            for (int tx = t0x; tx <= t1x; tx++) {
                long k = WorldState.key(tx, ty);
                tileNodes.computeIfAbsent(k, kk -> new IntBag()).add(x, y, radius);
                maskCache.remove(k);                                    // тайл придётся пересобрать
                if (k == lastKey) lastMask = null;
            }
    }

    /** Ленивая сборка битовой маски тайла: строка тайла = один long, заливка строки диска — одна операция. */
    private long[] buildMask(int tx, int ty, long tk) {
        long[] m = new long[TILE];
        IntBag bag = tileNodes.get(tk);
        if (bag != null) {
            int ox = tx << TILE_BITS, oy = ty << TILE_BITS;
            for (int i = 0; i < bag.n; i += 3) {
                int cx = bag.a[i], cy = bag.a[i + 1], r = bag.a[i + 2];
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
        }
        maskCache.put(tk, m);
        return m;
    }
}
