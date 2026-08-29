package mapgen.rivers;

import mapgen.core.World;
import mapgen.core.WorldState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Планировщик фазы трассировки.
 *
 * <p><b>Порядок регионов не зависит от порядка блоков.</b> В старом коде трассировка запускалась
 * из RiverGenerator, то есть порядок обхода блоков влиял на форму рек: сгенерировать 0..3, потом
 * 4..7 давало не тот мир, что 0..7 за один заход. Здесь список нужных регионов вычисляется по
 * границам всего запрошенного диапазона и обходится в каноническом порядке (ry, rx).
 *
 * <p><b>Параллельность.</b> Река, выросшая в регионе R, лежит целиком внутри
 * {@code influencePx} от границ R. Значит два региона независимы, если зазор между ними больше
 * удвоенного радиуса влияния — тогда ни одна река одного не может увидеть воду другого.
 * Отсюда {@link #SEPARATION_REGIONS}. Пачки набираются жадно в каноническом порядке: регион
 * попадает в текущую пачку, если он отстоит на SEPARATION от всех уже набранных. Пачка считается
 * параллельно, результаты вливаются в общую маску в порядке сортировки. Результат от числа
 * потоков не зависит.
 *
 * <p>Степень параллелизма растёт с площадью: минимальное расстояние между регионами пачки
 * фиксировано, поэтому на диапазоне 20x20 блоков в пачке оказывается ~4 региона, на 50x50 — ~9.
 * На маленьких мирах пачка вырождается в один регион и планировщик честно считает его на месте.
 */
public final class RiverPlanner {

    /** Насколько далеко от целевого диапазона нужно трассировать, чтобы река дотянулась внутрь. */
    public static int reachRegions() {
        return (int) Math.ceil((double) RiverTracer.influencePx() / RiverTracer.REGION_SIZE) + 1;
    }

    /**
     * Минимальное расстояние (в регионах) между регионами одной пачки.
     * Зазор между габаритами регионов на расстоянии d равен (d-1)*REGION_SIZE; требуем,
     * чтобы он превышал 2*influencePx.
     */
    public static final int SEPARATION_REGIONS =
            (int) Math.floor(2.0 * RiverTracer.influencePx() / RiverTracer.REGION_SIZE) + 2;

    public record Stats(int regionsTraced, int riversAdded, int batches, int maxBatchSize) {}

    private final World world;
    private final WorldState state;
    private final WaterMask mask;

    public RiverPlanner(World world, WorldState state, WaterMask mask) {
        this.world = world;
        this.state = state;
        this.mask = mask;
    }

    /** Регионы, покрывающие диапазон блоков вместе с запасом на дальность рек. */
    public static long[] regionsForChunks(int cx0, int cy0, int cx1, int cy1) {
        int rc = RiverTracer.REGION_CHUNKS, reach = reachRegions();
        int rx0 = Math.floorDiv(Math.min(cx0, cx1), rc) - reach;
        int rx1 = Math.floorDiv(Math.max(cx0, cx1), rc) + reach;
        int ry0 = Math.floorDiv(Math.min(cy0, cy1), rc) - reach;
        int ry1 = Math.floorDiv(Math.max(cy0, cy1), rc) + reach;
        long[] out = new long[(rx1 - rx0 + 1) * (ry1 - ry0 + 1)];
        int i = 0;
        for (int ry = ry0; ry <= ry1; ry++)
            for (int rx = rx0; rx <= rx1; rx++) out[i++] = WorldState.key(rx, ry);
        return out;
    }

    /**
     * Трассирует все ещё не пройденные регионы диапазона. Маска должна быть незамороженной;
     * после возврата её можно замораживать.
     */
    public Stats traceForChunks(int cx0, int cy0, int cx1, int cy1, ExecutorService pool, int threads) {
        List<long[]> pending = new ArrayList<>();
        for (long k : regionsForChunks(cx0, cy0, cx1, cy1)) {
            if (state.tracedRegions.contains(k)) continue;
            pending.add(new long[]{WorldState.keyY(k), WorldState.keyX(k), k});   // сортировка по (ry, rx)
        }
        pending.sort(Comparator.<long[]>comparingLong(a -> a[0]).thenComparingLong(a -> a[1]));

        int traced = 0, added = 0, batches = 0, maxBatch = 0;
        while (!pending.isEmpty()) {
            List<long[]> batch = new ArrayList<>();
            List<long[]> rest = new ArrayList<>();
            for (long[] r : pending) {
                if (independentOfAll(r, batch)) batch.add(r); else rest.add(r);
            }
            pending = rest;
            batches++;
            maxBatch = Math.max(maxBatch, batch.size());
            added += runBatch(batch, pool, threads);
            traced += batch.size();
        }
        return new Stats(traced, added, batches, maxBatch);
    }

    private static boolean independentOfAll(long[] cand, List<long[]> batch) {
        for (long[] b : batch) {
            long d = Math.max(Math.abs(cand[1] - b[1]), Math.abs(cand[0] - b[0]));
            if (d < SEPARATION_REGIONS) return false;
        }
        return true;
    }

    /** Считает пачку независимых регионов и детерминированно вливает результат. */
    private int runBatch(List<long[]> batch, ExecutorService pool, int threads) {
        List<Result> results = new ArrayList<>(batch.size());
        if (batch.size() == 1 || threads <= 1 || pool == null) {
            for (long[] r : batch) results.add(trace((int) r[1], (int) r[0]));
        } else {
            List<Future<Result>> futures = new ArrayList<>(batch.size());
            for (long[] r : batch) {
                int rx = (int) r[1], ry = (int) r[0];
                futures.add(pool.submit(() -> trace(rx, ry)));
            }
            for (Future<Result> f : futures) {
                try {
                    results.add(f.get());
                } catch (Exception e) {
                    throw new RuntimeException("трассировка региона упала", e.getCause() != null ? e.getCause() : e);
                }
            }
        }
        // порядок слияния = порядок batch (канонический), от числа потоков не зависит
        int added = 0;
        for (Result res : results) {
            state.tracedRegions.add(WorldState.key(res.rx, res.ry));
            for (River r : res.rivers) { state.rivers.add(r); added++; }
            if (!res.rivers.isEmpty()) mask.absorb(res.overlay);
        }
        return added;
    }

    private Result trace(int rx, int ry) {
        RiverTracer tracer = new RiverTracer(world, mask);
        List<River> rivers = tracer.traceRegion(rx, ry);
        return new Result(rx, ry, rivers, tracer.overlay());
    }

    private record Result(int rx, int ry, List<River> rivers, WaterMask overlay) {}
}
