package mapgen.rivers;

import mapgen.core.World;
import mapgen.core.WorldState;
import mapgen.util.LongHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Рост речных систем в одном регионе. Реки растут от устьев к истокам: направление ветви
 * выбирается по гладкому потенциалу стока {@link World#flow} (крупный уклон + извилистость),
 * а не по пиксельной высоте — иначе ветвь глохнет на первом же холме. Ветви с некоторой
 * вероятностью раздваиваются, останавливаются на хребтах, у чужих рек и при длительном спуске.
 * Ширина русла = f(число клеток выше по течению), поэтому ствол у устья широкий, а истоки — ручьи.
 *
 * <p>Экземпляр трассировщика принадлежит одному потоку. Он читает общую замороженную маску
 * (реки предыдущих проходов) и пишет в собственный оверлей, который координатор потом вливает
 * в общую маску. Регионы одного прохода разнесены дальше зоны влияния, поэтому оверлеи
 * независимы и порядок их слияния на результат не влияет.
 */
public final class RiverTracer {
    public static final int REGION_CHUNKS = 4;
    public static final int MOUTHS_PER_REGION = 2;
    public static final int MAX_BRANCH_LENGTH = 5000;   // длина одной ветви от устья, px
    public static final int MAX_NODES = 15000;          // суммарный размер дерева
    public static final int MAX_ACTIVE_TIPS = 4;
    public static final double BRANCH_PROB = 0.001;
    public static final int MIN_LENGTH_BEFORE_BRANCH = 80;
    public static final double RIDGE_DRAINAGE = 0.70;   // водораздел: выше — ветвь заканчивается (исток)
    public static final int MAX_DOWNHILL_STEPS = 150;   // сколько подряд шагов "вниз" по потенциалу терпим
    public static final int LOOKAHEAD = 16;             // на сколько px вперёд смотрим при выборе направления
    /** Сколько шагов бродит поиск устья от случайной точки озера. Входит в радиус влияния региона. */
    public static final int MOUTH_WALK = 400;

    // --- ширина русла ---
    public static final int MAX_WIDTH = 50;
    /** width = WIDTH_K * sqrt(клеток выше по течению): 500 -> 10 px, 3000 -> 25 px, 14000+ -> 50 px. */
    public static final double WIDTH_K = 0.42;

    // --- геометрия ветвления ---
    public static final double BRANCH_ANGLE_DEG = 62;
    public static final int BRANCH_BIAS_STEPS = 150;
    public static final double BRANCH_CONE_DEG = 45;
    public static final double BRANCH_MIN_ANGLE_DEG = 45;

    private static final float INV_SQRT2 = (float) (1 / Math.sqrt(2));
    private static final float BRANCH_CONE_COS = (float) Math.cos(Math.toRadians(BRANCH_CONE_DEG));
    private static final float BRANCH_MIN_COS = (float) Math.cos(Math.toRadians(BRANCH_MIN_ANGLE_DEG));
    private static final float BRANCH_ROT_COS = (float) Math.cos(Math.toRadians(BRANCH_ANGLE_DEG));
    private static final float BRANCH_ROT_SIN = (float) Math.sin(Math.toRadians(BRANCH_ANGLE_DEG));

    public static final int REGION_SIZE = REGION_CHUNKS * World.CHUNK_SIZE;

    /** Радиус влияния одного региона в пикселях: докуда может дотянуться выросшая в нём река. */
    public static int influencePx() { return MAX_BRANCH_LENGTH + MOUTH_WALK; }

    private final World world;
    private final WaterMask.View base;      // общая маска прошлых проходов, только чтение
    private final WaterMask overlay;        // собственный результат этого воркера
    private final WaterMask.View overlayView;

    public RiverTracer(World world, WaterMask base) {
        this.world = world;
        this.base = base.view();
        this.overlay = new WaterMask();
        this.overlayView = overlay.view();
    }

    public WaterMask overlay() { return overlay; }

    private boolean isWater(int x, int y) { return base.isWater(x, y) || overlayView.isWater(x, y); }

    /** Трассирует один регион; возвращает выросшие в нём реки (могут быть пустым списком). */
    public List<River> traceRegion(int rx, int ry) {
        Random rnd = new Random(world.seed() ^ (rx * 0x9E3779B97F4A7C15L) ^ (ry * 0xC2B2AE3D27D4EB4FL));
        int ox = rx * REGION_SIZE, oy = ry * REGION_SIZE;
        List<River> out = new ArrayList<>(MOUTHS_PER_REGION);
        for (int i = 0; i < MOUTHS_PER_REGION; i++) {
            for (int attempt = 0; attempt < 80; attempt++) {
                int[] mouth = findMouth(ox + rnd.nextInt(REGION_SIZE), oy + rnd.nextInt(REGION_SIZE), rnd);
                if (mouth == null) continue;
                River r = grow(mouth[0], mouth[1], rnd);
                if (r.nodeCount() < 40) continue;       // не вырос — пробуем другое устье
                out.add(r);
                overlay.add(r);                          // следующая река в этом же регионе его увидит
                break;
            }
        }
        return out;
    }

    /** Из случайной точки озера идём к самому высокому соседу, пока не выйдем на берег. */
    private int[] findMouth(int x, int y, Random rnd) {
        if (!world.isLake(x, y)) return null;
        for (int step = 0; step < MOUTH_WALK; step++) {
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
        int cap = 4096;
        int[] nx = new int[cap], ny = new int[cap], par = new int[cap];
        int n = 0;
        LongHashSet visited = new LongHashSet(1 << 14);
        Deque<Tip> tips = new ArrayDeque<>();

        // буферы кандидатов переиспользуются на всех шагах — без аллокаций в цикле роста
        int[] cdx = new int[8], cdy = new int[8];
        float[] cs = new float[8];

        nx[n] = mx; ny[n] = my; par[n] = -1; n++;
        visited.add(WorldState.key(mx, my));
        tips.add(new Tip(mx, my, 0, 0));

        while (!tips.isEmpty() && n < MAX_NODES) {
            Tip t = tips.poll();
            if (t.length >= MAX_BRANCH_LENGTH) continue;
            if (world.drainage(t.x, t.y) > RIDGE_DRAINAGE) continue;      // дошли до водораздела — исток
            if (world.height(t.x, t.y) > world.rockLevel()) continue;     // не лезем в скалы
            float h = world.flow(t.x, t.y);

            int parentNode = par[t.node];
            int px = parentNode >= 0 ? nx[parentNode] : Integer.MIN_VALUE;
            int py = parentNode >= 0 ? ny[parentNode] : Integer.MIN_VALUE;

            // допустимые соседи и их оценка по гладкому потенциалу с просмотром вперёд + шум
            int cnt = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int ax = t.x + dx, ay = t.y + dy;
                    if (!allowed(ax, ay, t.x, t.y, px, py, visited)) continue;
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

            if (n + 2 > cap) {
                cap <<= 1;
                nx = Arrays.copyOf(nx, cap); ny = Arrays.copyOf(ny, cap); par = Arrays.copyOf(par, cap);
            }

            nx[n] = bx; ny[n] = by; par[n] = t.node;
            visited.add(WorldState.key(bx, by));
            int idx = n++;
            Tip next = new Tip(bx, by, idx, t.length + 1);
            next.downhill = t.downhill;                                  // счётчик спуска живёт вдоль ветви
            if (t.biasLeft > 0) { next.biasLeft = t.biasLeft - 1; next.dirX = t.dirX; next.dirY = t.dirY; }
            tips.add(next);

            if (si >= 0) {
                int sx = t.x + cdx[si], sy = t.y + cdy[si];
                nx[n] = sx; ny[n] = sy; par[n] = t.node;
                visited.add(WorldState.key(sx, sy));
                int bidx = n++;
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
        int[] upstream = new int[n];
        Arrays.fill(upstream, 1);
        for (int i = n - 1; i > 0; i--) upstream[par[i]] += upstream[i];

        int[] nodes = new int[n * 3];
        for (int i = 0; i < n; i++) {
            nodes[i * 3] = nx[i];
            nodes[i * 3 + 1] = ny[i];
            nodes[i * 3 + 2] = widthPx(upstream[i]);
        }
        return River.detailed(mx, my, 0, nodes, Arrays.copyOf(par, n));
    }

    /** Лучший кандидат по потенциалу; при cone=true ветвь, удерживающая курс, ограничена конусом. */
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

    /** Клетка допустима, если не вода, не посещена и не касается чужих клеток дерева. */
    private boolean allowed(int ax, int ay, int cx, int cy, int px, int py, LongHashSet visited) {
        if (world.isLake(ax, ay) || isWater(ax, ay)) return false;
        if (visited.contains(WorldState.key(ax, ay))) return false;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int bx = ax + dx, by = ay + dy;
                if ((bx == cx && by == cy) || (bx == px && by == py)) continue;
                if (visited.contains(WorldState.key(bx, by))) return false;
            }
        return true;
    }
}
