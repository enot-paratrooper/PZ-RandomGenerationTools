package mapgen;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;
import mapgen.core.GenerationPipeline;
import mapgen.core.World;
import mapgen.core.WorldState;
import mapgen.generators.BaseSurfaceGenerator;
import mapgen.generators.RiverGenerator;
import mapgen.generators.VegetationGenerator;
import mapgen.io.ChunkStore;
import mapgen.io.RiverDebugExporter;
import mapgen.rivers.River;
import mapgen.rivers.RiverPlanner;
import mapgen.rivers.RiverSimplifier;
import mapgen.rivers.RiverTracer;
import mapgen.rivers.WaterMask;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * java -cp out mapgen.Main &lt;outDir&gt; &lt;seed&gt; &lt;cx0&gt; &lt;cy0&gt; &lt;cx1&gt; &lt;cy1&gt;
 *      [threads] [colorsMap.txt colorsMap_veg.txt]
 *
 * <p>Генерация идёт тремя фазами:
 * <ol>
 *   <li><b>Трассировка</b> — детерминированная, мутирующая. Считает все регионы, нужные диапазону,
 *       в каноническом порядке; пачки независимых регионов идут параллельно (RiverPlanner).</li>
 *   <li><b>Заморозка</b> — далёкие реки сжимаются в векторы, водная маска становится неизменяемой.</li>
 *   <li><b>Растеризация</b> — блоки считаются параллельно, писателей нет вообще.</li>
 * </ol>
 * Такое разделение чинит не только гонки, но и детерминизм: раньше порядок трассировки регионов
 * задавался порядком обхода блоков, и диапазон 0..7 за один заход давал не тот мир, что 0..3 + 4..7.
 *
 * <p>Результат: outDir/map.bmp, map_veg.bmp, debug_rivers.bmp, chunks/*.bmp, world.state
 */
public final class Main {

    /** Как часто сбрасывать состояние на диск. Раньше это делалось после каждого блока, что даёт O(N^2). */
    private static final int SAVE_EVERY = 16;

    /**
     * Реки дальше этого расстояния от генерируемой области хранятся в сжатой векторной форме.
     * Две реки могут дотянуться друг до друга максимум на 2 * MAX_BRANCH_LENGTH, плюс запас на
     * блуждание поиска устья и на размер региона.
     */
    public static final int SIMPLIFY_DISTANCE_PX =
            2 * RiverTracer.MAX_BRANCH_LENGTH + 2 * RiverTracer.MOUTH_WALK + RiverTracer.REGION_SIZE;

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.out.println("usage: Main <outDir> <seed> <cx0> <cy0> <cx1> <cy1> "
                    + "[threads] [colorsMap.txt colorsMap_veg.txt]");
            return;
        }
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        Path stateFile = out.resolve("world.state");

        int cx0 = Math.min(Integer.parseInt(args[2]), Integer.parseInt(args[4]));
        int cx1 = Math.max(Integer.parseInt(args[2]), Integer.parseInt(args[4]));
        int cy0 = Math.min(Integer.parseInt(args[3]), Integer.parseInt(args[5]));
        int cy1 = Math.max(Integer.parseInt(args[3]), Integer.parseInt(args[5]));

        int threads = args.length > 6 && !args[6].isEmpty()
                ? Integer.parseInt(args[6])
                : Runtime.getRuntime().availableProcessors();
        threads = Math.max(1, threads);

        WorldState state;
        if (Files.exists(stateFile)) {
            state = WorldState.load(stateFile);
            System.out.println("Продолжаем мир seed=" + state.seed
                    + ", блоков: " + state.generatedChunks.size() + ", рек: " + state.rivers.size());
        } else {
            state = new WorldState();
            state.seed = Long.parseLong(args[1]);
        }

        Palette palette = new Palette();
        if (args.length > 8) palette.validateAgainst(Path.of(args[7]), Path.of(args[8]));

        ImageIO.setUseCache(false);              // иначе воркеры дерутся за общий дисковый кэш ImageIO
        World world = new World(state.seed, palette, 0.37, 0.70);
        WaterMask mask = new WaterMask();
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "mapgen");
            t.setDaemon(true);
            return t;
        });

        try {
            // ---------- фаза 1: сжатие дальних рек и наполнение маски ранее известной водой ----------
            long t0 = System.currentTimeMillis();
            int simplified = compactFarRivers(state, cx0, cy0, cx1, cy1);
            for (River r : state.rivers) mask.add(r);

            // ---------- фаза 1: трассировка недостающих регионов ----------
            RiverPlanner planner = new RiverPlanner(world, state, mask);
            RiverPlanner.Stats st = planner.traceForChunks(cx0, cy0, cx1, cy1, pool, threads);
            System.out.printf("Трассировка: регионов %d, новых рек %d, пачек %d (макс. %d параллельно), %d мс%n",
                    st.regionsTraced(), st.riversAdded(), st.batches(), st.maxBatchSize(),
                    System.currentTimeMillis() - t0);
            if (simplified > 0)
                System.out.printf("Сжато в векторы: %d рек (дальше %d px от диапазона), в памяти рек ~%d КБ%n",
                        simplified, SIMPLIFY_DISTANCE_PX, riverBytes(state) >> 10);

            // ---------- фаза 2: заморозка ----------
            mask.freeze();
            state.save(stateFile);

            // ---------- фаза 3: параллельная растеризация ----------
            ChunkStore store = new ChunkStore(out);
            World w = world;
            WaterMask m = mask;
            ThreadLocal<GenContext> contexts =
                    ThreadLocal.withInitial(() -> new GenContext(w, m, World.CHUNK_SIZE));
            GenerationPipeline pipeline = new GenerationPipeline()
                    .add(new BaseSurfaceGenerator())
                    .add(new RiverGenerator())
                    // .add(new RoadGenerator())   // будущее: дороги — blockVegetation под полотном
                    // .add(new TownGenerator())   // будущее: города
                    .add(new VegetationGenerator()); // всегда последний

            List<long[]> todo = new ArrayList<>();
            for (int cy = cy0; cy <= cy1; cy++)
                for (int cx = cx0; cx <= cx1; cx++) {
                    long key = WorldState.key(cx, cy);
                    if (!state.generatedChunks.contains(key)) todo.add(new long[]{cx, cy, key});
                }

            long t1 = System.currentTimeMillis();
            List<Future<String>> futures = new ArrayList<>(todo.size());
            for (long[] c : todo) {
                int cx = (int) c[0], cy = (int) c[1];
                futures.add(pool.submit(() -> {
                    Chunk chunk = new Chunk(cx, cy, World.CHUNK_SIZE);
                    String report = pipeline.run(contexts.get(), chunk);
                    store.save(chunk);
                    return report;
                }));
            }
            // результаты забирает координатор — только он трогает WorldState
            int done = 0;
            for (int i = 0; i < futures.size(); i++) {
                String report;
                try {
                    report = futures.get(i).get();
                } catch (Exception e) {
                    pool.shutdownNow();
                    throw new RuntimeException("генерация блока упала", e.getCause() != null ? e.getCause() : e);
                }
                System.out.println("  " + report);
                state.generatedChunks.add(todo.get(i)[2]);
                if (++done % SAVE_EVERY == 0) state.save(stateFile);
            }
            state.save(stateFile);
            if (!todo.isEmpty())
                System.out.printf("Растеризация: %d блоков за %d мс на %d потоках (%.1f мс/блок)%n",
                        todo.size(), System.currentTimeMillis() - t1, threads,
                        (System.currentTimeMillis() - t1) / (double) todo.size());

            store.stitch(state, out.resolve("map.bmp"), out.resolve("map_veg.bmp"));
            RiverDebugExporter.export(world, state, mask, out.resolve("debug_rivers.bmp"));
            System.out.println("Рек: " + state.rivers.size() + ", блоков: " + state.generatedChunks.size()
                    + " -> " + out.toAbsolutePath());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Переводит в векторную форму реки, удалённые от генерируемой области дальше
     * {@link #SIMPLIFY_DISTANCE_PX}. Такая река в этом прогоне не попадёт ни в один блок, а её
     * геометрия нужна только для того, чтобы новые реки в неё не вросли — для этого хватает
     * силуэта из {@link River#MAX_VECTORS} отрезков.
     *
     * <p>Границы берутся с запасом на радиус влияния региона: реки, которые ещё могут столкнуться
     * с новыми, остаются детальными.
     */
    private static int compactFarRivers(WorldState state, int cx0, int cy0, int cx1, int cy1) {
        int cs = World.CHUNK_SIZE;
        int x0 = cx0 * cs, y0 = cy0 * cs, x1 = (cx1 + 1) * cs - 1, y1 = (cy1 + 1) * cs - 1;
        int changed = 0;
        for (int i = 0; i < state.rivers.size(); i++) {
            River r = state.rivers.get(i);
            if (r.simplified() || !r.hasTree()) continue;
            if (r.distanceTo(x0, y0, x1, y1) <= SIMPLIFY_DISTANCE_PX) continue;
            River s = RiverSimplifier.simplify(r, River.MAX_VECTORS);
            if (s != r) { state.rivers.set(i, s); changed++; }
        }
        return changed;
    }

    private static long riverBytes(WorldState state) {
        long sum = 0;
        for (River r : state.rivers) sum += r.approxBytes();
        return sum;
    }
}
