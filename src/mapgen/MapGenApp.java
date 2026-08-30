package mapgen;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;
import mapgen.core.GenerationPipeline;
import mapgen.core.World;
import mapgen.core.WorldState;
import mapgen.generators.BaseSurfaceGenerator;
import mapgen.generators.RiverGenerator;
import mapgen.generators.TownGenerator;
import mapgen.generators.VegetationGenerator;
import mapgen.io.ChunkStore;
import mapgen.io.RiverDebugExporter;
import mapgen.io.TmxStore;
import mapgen.io.TmxTemplate;
import mapgen.io.TownDebugExporter;
import mapgen.rivers.River;
import mapgen.rivers.RiverPlanner;
import mapgen.rivers.RiverSimplifier;
import mapgen.rivers.RiverTracer;
import mapgen.rivers.WaterMask;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * AI: Фазы генерации мира, вынесенные из {@link Main}. В {@code main} остались только вызовы,
 * порядок которых и есть описание конвейера.
 *
 * <p>Фаз три, и делятся они по отношению к изменяемому состоянию:
 * <ol>
 *   <li><b>Трассировка</b> — детерминированная, мутирующая. Считает все регионы, нужные диапазону,
 *       в каноническом порядке; пачки независимых регионов идут параллельно (RiverPlanner).</li>
 *   <li><b>Заморозка</b> — далёкие реки сжимаются в векторы, водная маска становится неизменяемой.</li>
 *   <li><b>Растеризация</b> — блоки считаются параллельно, писателей нет вообще.</li>
 * </ol>
 * Такое разделение чинит не только гонки, но и детерминизм: раньше порядок трассировки регионов
 * задавался порядком обхода блоков, и диапазон 0..7 за один заход давал не тот мир, что 0..3 + 4..7.
 *
 * <p>Города собственной фазы не требуют: их геометрия — чистая функция от (seed, координаты),
 * поэтому TownGenerator работает прямо в фазе 3 (см. mapgen.towns.TownField).
 *
 * <p>Основной результат — ячейки {@code <префикс>_<cx>_<cy>.tmx} в outDir плюс {@code world.state}.
 * Растровая отладка (chunks/*.bmp, map.bmp, map_veg.bmp, debug_rivers.bmp, debug_towns.bmp)
 * пишется только с флагом {@code debug}: на больших диапазонах она стоит дороже самой генерации
 * и упирается в память при склейке.
 *
 * <p>Объект живёт один прогон и принадлежит потоку, который его создал; параллельны только воркеры
 * внутри {@link #rasterizeChunks()}.
 */
public final class MapGenApp implements AutoCloseable {

    /** Как часто сбрасывать состояние на диск. Раньше это делалось после каждого блока, что даёт O(N^2). */
    private static final int SAVE_EVERY = 16;

    /**
     * Реки дальше этого расстояния от генерируемой области хранятся в сжатой векторной форме.
     * Две реки могут дотянуться друг до друга максимум на 2 * MAX_BRANCH_LENGTH, плюс запас на
     * блуждание поиска устья и на размер региона.
     */
    public static final int SIMPLIFY_DISTANCE_PX =
            2 * RiverTracer.MAX_BRANCH_LENGTH + 2 * RiverTracer.MOUTH_WALK + RiverTracer.REGION_SIZE;

    /** Как называется шаблон ячейки, если его не указали явно. */
    public static final String DEFAULT_TEMPLATE = "template.tmx";
    public static final String DEFAULT_TEMPLATE_PATH = "..\\RandomRoomGenerator\\conf\\mapTemplate\\template.tmx";

    /** Разобранная командная строка. Диапазон уже нормализован: cx0 <= cx1, cy0 <= cy1. */
    public record Options(Path outDir, long seed, int cx0, int cy0, int cx1, int cy1, int threads,
                          Path colorsMap, Path colorsVegMap, Path template, boolean debug) {}

    private final Options options;
    private final Path stateFile;
    private final TmxStore tmx;
    /** null, если отладочные картинки не запрашивали. */
    private final ChunkStore images;
    private final ExecutorService pool;
    private final WaterMask mask = new WaterMask();

    private WorldState state;
    private World world;
    private int rasterized;

    // ------------------------------------------------------------------ разбор аргументов

    /**
     * Позиционные аргументы плюс именованные флаги в любом месте строки.
     *
     * @return null, если аргументов не хватает — вызывающий печатает подсказку
     */
    public static Options parseArgs(String[] args) {
        List<String> positional = new ArrayList<>(args.length);
        boolean debug = false;
        Path template = null;
        for (String a : args) {
            String low = a.toLowerCase();
            if (low.equals("debug") || low.equals("-debug") || low.equals("--debug")) debug = true;
            else if (low.startsWith("template=")) template = Path.of(a.substring("template=".length()));
            else positional.add(a);
        }
        if (positional.size() < 6) return null;

        int ax = Integer.parseInt(positional.get(2)), ay = Integer.parseInt(positional.get(3));
        int bx = Integer.parseInt(positional.get(4)), by = Integer.parseInt(positional.get(5));
        int threads = positional.size() > 6 && !positional.get(6).isEmpty()
                ? Integer.parseInt(positional.get(6))
                : Runtime.getRuntime().availableProcessors();

        return new Options(Path.of(positional.get(0)), Long.parseLong(positional.get(1)),
                Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by),
                Math.max(1, threads),
                positional.size() > 8 ? Path.of(positional.get(7)) : null,
                positional.size() > 8 ? Path.of(positional.get(8)) : null,
                template, debug);
    }

    public static void printUsage() {
        System.out.println("usage: Main <outDir> <seed> <cx0> <cy0> <cx1> <cy1> "
                + "[threads] [colorsMap.txt colorsMap_veg.txt] [template=<файл.tmx>] [debug]");
        System.out.println("  template=  шаблон ячейки WorldEd; по умолчанию ищется "
                + DEFAULT_TEMPLATE + " в outDir, затем в текущем каталоге");
        System.out.println("  debug      дополнительно писать картинки: chunks/*.bmp, map.bmp, "
                + "map_veg.bmp, debug_rivers.bmp, debug_towns.bmp");
    }

    // ------------------------------------------------------------------ подготовка

    public MapGenApp(Options options) throws IOException {
        this.options = options;
        Files.createDirectories(options.outDir());
        this.stateFile = options.outDir().resolve("world.state");
        this.tmx = new TmxStore(options.outDir(), TmxTemplate.load(resolveTemplate(options)));
        this.images = options.debug() ? new ChunkStore(options.outDir()) : null;
        this.pool = Executors.newFixedThreadPool(options.threads(), r -> {
            Thread t = new Thread(r, "mapgen");
            t.setDaemon(true);
            return t;
        });
        ImageIO.setUseCache(false);  // иначе воркеры дерутся за общий дисковый кэш ImageIO
    }

    /** Явный путь, иначе {@code outDir/template.tmx}, иначе {@code ./template.tmx}. */
    private static Path resolveTemplate(Options o) throws IOException {
        if (o.template() != null) return o.template();
        Path inOut = o.outDir().resolve(DEFAULT_TEMPLATE);
        if (Files.exists(inOut)) return inOut;
        Path inCwd = Path.of(DEFAULT_TEMPLATE_PATH);
        if (Files.exists(inCwd)) return inCwd;
        throw new java.io.FileNotFoundException("шаблон ячейки не найден: ни " + inOut.toAbsolutePath()
                + ", ни " + inCwd.toAbsolutePath() + ". Укажите его через template=<файл.tmx>");
    }

    /** Продолжаем существующий мир или начинаем новый с seed из аргументов. */
    public void loadState() throws IOException {
        if (Files.exists(stateFile)) {
            state = WorldState.load(stateFile);
            System.out.println("Продолжаем мир seed=" + state.seed
                    + ", блоков: " + state.generatedChunks.size() + ", рек: " + state.rivers.size());
        } else {
            state = new WorldState();
            state.seed = options.seed();
        }
    }

    public void buildWorld() throws Exception {
        Palette palette = new Palette();
        if (options.colorsMap() != null)
            palette.validateAgainst(options.colorsMap(), options.colorsVegMap());
        world = new World(state.seed, palette, 0.37, 0.70);
    }

    // ------------------------------------------------------------------ фаза 1: реки

    /** Сжимает дальние реки и наполняет маску ранее известной водой. */
    public void prepareKnownRivers() {
        int simplified = compactFarRivers();
        for (River r : state.rivers) mask.add(r);
        if (simplified > 0)
            System.out.printf("Сжато в векторы: %d рек (дальше %d px от диапазона), в памяти рек ~%d КБ%n",
                    simplified, SIMPLIFY_DISTANCE_PX, riverBytes() >> 10);
    }

    public void traceRivers() {
        long t0 = System.currentTimeMillis();
        RiverPlanner planner = new RiverPlanner(world, state, mask);
        RiverPlanner.Stats st = planner.traceForChunks(
                options.cx0(), options.cy0(), options.cx1(), options.cy1(), pool, options.threads());
        System.out.printf("Трассировка: регионов %d, новых рек %d, пачек %d (макс. %d параллельно), %d мс%n",
                st.regionsTraced(), st.riversAdded(), st.batches(), st.maxBatchSize(),
                System.currentTimeMillis() - t0);
    }

    // ------------------------------------------------------------------ фаза 2: заморозка

    public void freezeWater() throws IOException {
        mask.freeze();
        state.save(stateFile);
    }

    // ------------------------------------------------------------------ фаза 3: растеризация

    /**
     * Считает недостающие блоки параллельно и пишет их ячейки .tmx. Воркеры не трогают
     * {@link WorldState}: результаты забирает координатор, он же сбрасывает состояние на диск.
     */
    public void rasterizeChunks() throws Exception {
        World w = world;
        WaterMask m = mask;
        ThreadLocal<GenContext> contexts =
                ThreadLocal.withInitial(() -> new GenContext(w, m, World.CHUNK_SIZE));
        GenerationPipeline pipeline = new GenerationPipeline()
                .add(new BaseSurfaceGenerator())
                .add(new RiverGenerator())
                .add(new TownGenerator())        // улицы + заглушка застройки
                .add(new VegetationGenerator()); // всегда последний

        List<long[]> todo = pendingChunks();
        long t1 = System.currentTimeMillis();
        List<Future<String>> futures = new ArrayList<>(todo.size());
        for (long[] c : todo) {
            int cx = (int) c[0], cy = (int) c[1];
            futures.add(pool.submit(() -> {
                Chunk chunk = new Chunk(cx, cy, World.CHUNK_SIZE);
                String report = pipeline.run(contexts.get(), chunk);
                tmx.write(chunk);
                if (images != null) images.save(chunk);
                return report;
            }));
        }

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
        rasterized = todo.size();

        if (!todo.isEmpty())
            System.out.printf("Растеризация: %d блоков за %d мс на %d потоках (%.1f мс/блок)%n",
                    todo.size(), System.currentTimeMillis() - t1, options.threads(),
                    (System.currentTimeMillis() - t1) / (double) todo.size());
    }

    // ------------------------------------------------------------------ вывод

    /** Растровая отладка. Без флага debug не пишется ничего: основной формат теперь .tmx. */
    public void exportDebugImages() throws Exception {
        if (images == null) return;
        images.stitch(state, options.outDir().resolve("map.bmp"), options.outDir().resolve("map_veg.bmp"));
        RiverDebugExporter.export(world, state, mask, options.outDir().resolve("debug_rivers.bmp"));
        TownDebugExporter.export(world, state, mask, options.outDir().resolve("debug_towns.bmp"));
    }

    public void printSummary() {
        System.out.printf("Ячейки: %s_<cx>_<cy>.tmx, записано за прогон %d%n",
                tmx.template().namePrefix(), rasterized);
        System.out.println("Рек: " + state.rivers.size() + ", блоков: " + state.generatedChunks.size()
                + " -> " + options.outDir().toAbsolutePath());
    }

    @Override public void close() {
        pool.shutdown();
    }

    // ------------------------------------------------------------------ внутреннее

    /** Блоки диапазона, которых ещё нет в состоянии, в каноническом порядке обхода. */
    private List<long[]> pendingChunks() {
        List<long[]> todo = new ArrayList<>();
        for (int cy = options.cy0(); cy <= options.cy1(); cy++)
            for (int cx = options.cx0(); cx <= options.cx1(); cx++) {
                long key = WorldState.key(cx, cy);
                if (!state.generatedChunks.contains(key)) todo.add(new long[]{cx, cy, key});
            }
        return todo;
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
    private int compactFarRivers() {
        int cs = World.CHUNK_SIZE;
        int x0 = options.cx0() * cs, y0 = options.cy0() * cs;
        int x1 = (options.cx1() + 1) * cs - 1, y1 = (options.cy1() + 1) * cs - 1;
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

    private long riverBytes() {
        long sum = 0;
        for (River r : state.rivers) sum += r.approxBytes();
        return sum;
    }
}
