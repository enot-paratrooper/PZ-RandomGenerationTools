package mapgen.buildings;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;
import mapgen.towns.BuildingPlacer;
import mapgen.towns.DistrictType;
import mapgen.towns.InfraType;
import mapgen.towns.Town;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI: Настоящая застройка вместо {@code StubBuildingPlacer}: под каждый участок квартала
 * подбирается .tbx из пула, доворачивается фасадом к улице и уходит в
 * {@code <objectgroup name="Lots">} той ячейки, которой принадлежит его левый верхний угол.
 *
 * <h2>Как принимается решение</h2>
 * <ol>
 *   <li>Зона выбирается по типу района ({@link ZoneMix}) — «жилой квартал» это 90% частного
 *       сектора и 10% парковых объектов, «промышленный» — 85% промки и 15% автосервиса.</li>
 *   <li>Улица определяется по ближайшей стороне квартала: участки нарезаны сеткой внутри
 *       квартала, а квартал со всех сторон окружён полосами, поэтому «ближайшая сторона» и есть
 *       та, куда должен смотреть фасад.</li>
 *   <li>Постройка подбирается взвешенно по площади среди тех, что влезают в участок хоть в
 *       какой-нибудь ориентации; постройка из семейства, уже стоящего в этом квартале рядом,
 *       получает штраф {@link #FAMILY_PENALTY}, иначе серия одинаковых домов встаёт подряд.</li>
 *   <li>Число поворотов считается из {@code facing} записи. Если после нужного поворота габарит
 *       в участок не влезает, берётся поворот другой чётности — фасад тогда смотрит на боковую
 *       сторону, но здание хотя бы стоит.</li>
 *   <li>Второй проход добивает двор мелочью из {@code FILLER_PROP} — сараем, гаражом, патио.</li>
 * </ol>
 *
 * <h2>Детерминизм</h2>
 * Единственный источник случайности — {@code World.random("lot", x, y)} от координат участка,
 * поэтому один и тот же участок даёт один и тот же дом в любом потоке, при любом порядке обхода
 * блоков и при догенерации соседнего диапазона. Обход участков квартала идёт целиком, даже если
 * в ячейку попала лишь его часть: «память семейств» должна быть одинаковой по обе стороны шва.
 *
 * <h2>Кто владеет зданием</h2>
 * Здание пишется в ячейку, содержащую его левый верхний угол, и ровно один раз: WorldEd читает
 * лот как подкарту со смещением и сам разбирается, что она вылезла за границу ячейки. Растровый
 * след (запрет растительности) наносится во всех задетых ячейках с обрезкой.
 *
 * <h2>Многопоточность</h2>
 * Изменяемых полей нет, кроме потокобезопасного набора уже напечатанных предупреждений и
 * {@link RotatedTbxCache}, который синхронизирован внутри себя.
 */
public final class CatalogBuildingPlacer implements BuildingPlacer {

    /** Отступ здания от кромки участка со стороны улицы. */
    public static final int STREET_INSET = 0;
    /** Насколько реже выбирается постройка из семейства, уже стоящего рядом. */
    public static final double FAMILY_PENALTY = 0.08;
    /** Сколько предыдущих участков квартала помнить при штрафе за семейство. */
    public static final int FAMILY_MEMORY = 3;
    /** Минимальная сторона свободного двора, в который ещё имеет смысл ставить мелочь. */
    public static final int FILLER_MIN_SIDE = 8;
    /** Доля дворов, которые добиваются мелочью. */
    public static final double FILLER_CHANCE = 0.35;
    /** Шаг проверки участка на воду. Дом 20x20 проверяется 5x5 точками плюс углы. */
    public static final int WATER_SAMPLE_STEP = 4;
    /**
     * Закрашивать ли след здания на базовом слое. Само здание рисует .tbx, поэтому по делу
     * не нужно; включается, когда надо увидеть застройку на обзорном растре.
     */
    public static final boolean PAINT_FOOTPRINT = false;

    private final BuildingCatalog catalog;
    private final RotatedTbxCache rotated;
    /** Путь к каталогу построек относительно каталога ячеек, с завершающим слэшем. */
    private final String buildingsPrefix;
    /** То же для каталога повёрнутых копий. */
    private final String rotatedPrefix;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public CatalogBuildingPlacer(BuildingCatalog catalog, RotatedTbxCache rotated,
                                 String buildingsPrefix, String rotatedPrefix) {
        this.catalog = catalog;
        this.rotated = rotated;
        this.buildingsPrefix = withSlash(buildingsPrefix);
        this.rotatedPrefix = withSlash(rotatedPrefix);
    }

    private static String withSlash(String prefix) {
        String p = prefix.replace('\\', '/');
        return p.isEmpty() || p.endsWith("/") ? p : p + "/";
    }

    /** Выбранная постройка на своём месте в мировых координатах. */
    private record Placed(BuildingDef def, int turns, int x0, int y0, int w, int h) {
        int x1() { return x0 + w - 1; }
        int y1() { return y0 + h - 1; }
    }

    // ------------------------------------------------------------------ рядовая застройка

    @Override
    public void place(GenContext ctx, Chunk chunk, Town town, Town.Block block) {
        int n = block.lotCount();
        if (n == 0) return;

        DistrictType district = block.type();
        String[] recent = new String[FAMILY_MEMORY];
        int recentAt = 0;

        for (int i = 0; i < n; i++) {
            int lx0 = block.lotX0(i), ly0 = block.lotY0(i);
            int lx1 = block.lotX1(i), ly1 = block.lotY1(i);

            Random rnd = ctx.world().random("lot", lx0, ly0);
            int side = streetSide(block, lx0, ly0, lx1, ly1);

            Placed main = choose(ctx, catalog.zone(ZoneMix.zoneFor(district, rnd)),
                    lx0, ly0, lx1, ly1, side, rnd, recent);
            if (main == null) continue;

            recent[recentAt] = main.def().family();
            recentAt = (recentAt + 1) % FAMILY_MEMORY;

            emit(ctx, chunk, main);

            Placed filler = chooseFiller(ctx, district, lx0, ly0, lx1, ly1, side, main, rnd);
            if (filler != null) emit(ctx, chunk, filler);
        }
    }

    // ------------------------------------------------------------------ инфраструктура

    @Override
    public void placeFacility(GenContext ctx, Chunk chunk, Town town, Town.Facility f) {
        InfraType type = f.type();
        BuildingCatalog.Pool pool = catalog.zone(ZoneMix.zoneFor(type));
        String role = ZoneMix.roleFor(type);
        if (role != null) {
            BuildingCatalog.Pool byRole = pool.role(role);
            if (byRole != null && !byRole.isEmpty()) pool = byRole;
        }

        int lotW = f.width(), lotH = f.height();
        // AI: фасад смотрит в сторону города — с этой стороны к объекту и подходит дорога,
        // хоть городская улица, хоть подъезд к вынесенному за черту зданию.
        int side = towardsTown(town, f);

        Placed p = fit(pool.largestFitting(lotW, lotH), lotW, lotH, f.x0(), f.y0(), side);
        if (p == null) {
            if (reported.add(type.name()))
                System.err.printf("ЗАСТРОЙКА: под «%s» нет постройки размером до %dx%d "
                        + "в зоне %s%s%n", type.label, lotW, lotH, ZoneMix.zoneFor(type),
                        role == null ? "" : " (роль " + role + ")");
            return;
        }
        if (isWet(ctx, p)) return;
        emit(ctx, chunk, p);
    }

    // ------------------------------------------------------------------ подбор

    /**
     * Подбор под участок: взвешенно по площади, затем доворот фасада. Возвращает {@code null},
     * если ничего не влезло или место оказалось мокрым.
     */
    private Placed choose(GenContext ctx, BuildingCatalog.Pool pool,
                          int lx0, int ly0, int lx1, int ly1, int side,
                          Random rnd, String[] recent) {
        int lotW = lx1 - lx0 + 1, lotH = ly1 - ly0 + 1;
        if (pool.isEmpty() || lotW < BuildingCatalog.MIN_SIDE || lotH < BuildingCatalog.MIN_SIDE) return null;

        BuildingDef def = pool.pick(lotW, lotH, rnd, recent, FAMILY_PENALTY);
        Placed p = fit(def, lotW, lotH, lx0, ly0, side);
        if (p == null || isWet(ctx, p)) return null;
        return p;
    }

    /**
     * Ставит выбранную постройку в участок: считает поворот под нужный фасад и прижимает
     * здание к стороне улицы, центрируя по другой оси.
     *
     * <p>Поворот выбирается так: сначала тот, что даёт нужный фасад; если после него габарит
     * не влезает — тот же плюс один (другая чётность, значит другие габариты); если и это не
     * подошло — участок пропускается. Обратный поворот (k + 2) даёт те же габариты, что и k,
     * поэтому проверять его смысла нет.
     */
    private Placed fit(BuildingDef def, int lotW, int lotH, int lx0, int ly0, int side) {
        if (def == null) return null;
        int want = def.turnsTo(side);
        int turns = def.fitsAfter(want, lotW, lotH) ? want
                  : def.fitsAfter((want + 1) & 3, lotW, lotH) ? (want + 1) & 3
                  : -1;
        if (turns < 0) return null;

        int w = def.widthAfter(turns), h = def.heightAfter(turns);
        int x0, y0;
        switch (side) {
            case 0 -> { x0 = lx0 + (lotW - w) / 2; y0 = ly0 + STREET_INSET; }                 // N
            case 1 -> { x0 = lx0 + lotW - w - STREET_INSET; y0 = ly0 + (lotH - h) / 2; }      // E
            case 2 -> { x0 = lx0 + (lotW - w) / 2; y0 = ly0 + lotH - h - STREET_INSET; }      // S
            default -> { x0 = lx0 + STREET_INSET; y0 = ly0 + (lotH - h) / 2; }                // W
        }
        // AI: STREET_INSET может выдавить здание за кромку участка, если оно ровно в него влезло.
        x0 = Math.max(lx0, Math.min(x0, lx0 + lotW - w));
        y0 = Math.max(ly0, Math.min(y0, ly0 + lotH - h));
        return new Placed(def, turns, x0, y0, w, h);
    }

    /**
     * Мелочь во двор: свободная полоса участка с противоположной от улицы стороны. Двор уже
     * занят домом, поэтому сарай не может встать на угол перекрёстка — ради этого второй проход
     * и заведён.
     */
    private Placed chooseFiller(GenContext ctx, DistrictType district,
                                int lx0, int ly0, int lx1, int ly1, int side,
                                Placed main, Random rnd) {
        if (rnd.nextDouble() > FILLER_CHANCE) return null;

        int yx0, yy0, yx1, yy1;
        switch (side) {
            case 0 -> { yx0 = lx0; yy0 = main.y1() + 1; yx1 = lx1; yy1 = ly1; }   // улица севернее
            case 1 -> { yx0 = lx0; yy0 = ly0; yx1 = main.x0() - 1; yy1 = ly1; }   // улица восточнее
            case 2 -> { yx0 = lx0; yy0 = ly0; yx1 = lx1; yy1 = main.y0() - 1; }   // улица южнее
            default -> { yx0 = main.x1() + 1; yy0 = ly0; yx1 = lx1; yy1 = ly1; }  // улица западнее
        }
        int w = yx1 - yx0 + 1, h = yy1 - yy0 + 1;
        if (w < FILLER_MIN_SIDE || h < FILLER_MIN_SIDE) return null;

        BuildingCatalog.Pool pool = catalog.zone(ZoneMix.fillerZoneFor(district));
        String role = ZoneMix.fillerRoleFor(district);
        if (role != null) {
            BuildingCatalog.Pool byRole = pool.role(role);
            if (byRole != null && !byRole.isEmpty()) pool = byRole;
        }
        if (pool.isEmpty()) return null;

        BuildingDef def = pool.pick(w, h, rnd, null, 1.0);
        if (def == null) return null;
        // AI: мелочь ставится в глубину двора, то есть фасадом от улицы — сарай к дороге задом.
        Placed p = fit(def, w, h, yx0, yy0, (side + 2) & 3);
        if (p == null || isWet(ctx, p)) return null;
        return p;
    }

    // ------------------------------------------------------------------ вывод

    /**
     * Наносит след здания на текущую ячейку и, если ячейка владеет левым верхним углом,
     * добавляет объект лота.
     */
    private void emit(GenContext ctx, Chunk chunk, Placed p) {
        mark(ctx, chunk, p);
        if (!owns(chunk, p.x0(), p.y0())) return;

        String path = pathOf(p);
        if (path == null) return;
        chunk.addLot(path, p.x0() - chunk.worldX(0), p.y0() - chunk.worldY(0), p.w(), p.h());
    }

    /**
     * Ссылка на .tbx относительно каталога ячеек; {@code null}, если повернуть не вышло.
     *
     * <p>Если поворот сорвался (файла нет, разметка не по формату), пробуется поворот на 180
     * от нужного: габарит у него тот же самый, значит здание всё равно встанет в размеченное
     * место — просто фасадом в противоположную сторону. Терять дом целиком из-за одного
     * нечитаемого исходника хуже, чем поставить его задом к дороге.
     */
    private String pathOf(Placed p) {
        String path = resolve(p.def(), p.turns());
        return path != null ? path : resolve(p.def(), (p.turns() + 2) & 3);
    }

    private String resolve(BuildingDef def, int turns) {
        if (turns == 0) return buildingsPrefix + def.path();
        String name = rotated.fileName(def.path(), turns);
        return name == null ? null : rotatedPrefix + name;
    }

    private static boolean owns(Chunk chunk, int wx, int wy) {
        return Math.floorDiv(wx, chunk.size) == chunk.cx && Math.floorDiv(wy, chunk.size) == chunk.cy;
    }

    /**
     * Запрещает растительность под зданием: иначе на крыше вырастет лес — .tbx кладёт тайлы
     * поверх, а слой растительности генерируется независимо.
     */
    private static void mark(GenContext ctx, Chunk chunk, Placed p) {
        Palette palette = ctx.world().palette();
        int ox = chunk.worldX(0), oy = chunk.worldY(0);
        int mx = ox + chunk.size - 1, my = oy + chunk.size - 1;
        int x0 = Math.max(ox, p.x0()), x1 = Math.min(mx, p.x1());
        int y0 = Math.max(oy, p.y0()), y1 = Math.min(my, p.y1());
        for (int wy = y0; wy <= y1; wy++) {
            for (int wx = x0; wx <= x1; wx++) {
                int x = wx - ox, y = wy - oy;
                chunk.blockVegetation(x, y);
                if (PAINT_FOOTPRINT && !palette.isRoad(chunk.base().get(x, y)))
                    chunk.base().set(x, y, palette.dirtGrass);
            }
        }
    }

    // ------------------------------------------------------------------ мелочи

    /**
     * Сторона квартала, к которой прижат участок: 0=N, 1=E, 2=S, 3=W. Порядок разбора ничьей
     * фиксирован, поэтому результат не зависит ни от чего, кроме геометрии.
     */
    private static int streetSide(Town.Block b, int lx0, int ly0, int lx1, int ly1) {
        int north = ly0 - b.y0(), south = b.y1() - ly1;
        int west = lx0 - b.x0(), east = b.x1() - lx1;
        int best = north, side = 0;
        if (east < best) { best = east; side = 1; }
        if (south < best) { best = south; side = 2; }
        if (west < best) { side = 3; }
        return side;
    }

    /** Сторона объекта, обращённая к центру города. */
    private static int towardsTown(Town town, Town.Facility f) {
        int dx = town.cx() - (f.x0() + f.x1()) / 2;
        int dy = town.cy() - (f.y0() + f.y1()) / 2;
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? 1 : 3;
        return dy >= 0 ? 2 : 0;
    }

    /**
     * Есть ли вода под зданием. Проверка идёт по сетке с шагом {@link #WATER_SAMPLE_STEP}
     * плюс правый и нижний края: дом 20x20 на берегу так не поставится, а полный обход
     * пикселей на каждую попытку подбора стоил бы дороже самой застройки.
     */
    private static boolean isWet(GenContext ctx, Placed p) {
        for (int y = p.y0();; y = Math.min(p.y1(), y + WATER_SAMPLE_STEP)) {
            for (int x = p.x0();; x = Math.min(p.x1(), x + WATER_SAMPLE_STEP)) {
                if (ctx.isWater(x, y)) return true;
                if (x == p.x1()) break;
            }
            if (y == p.y1()) break;
        }
        return false;
    }
}
