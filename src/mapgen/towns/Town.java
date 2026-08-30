package mapgen.towns;

/**
 * Готовая геометрия одного города в мировых координатах. Объект неизменяем и целиком
 * определяется парой (seed, координаты ячейки), поэтому его можно строить в любом потоке
 * и в любом порядке — результат совпадёт (см. {@link TownField}).
 *
 * <p>След города лежит внутри своей ячейки сетки {@link TownField#CELL}, поэтому пиксель
 * принадлежит максимум одному городу. Единственное исключение — вынесенные за черту
 * инфраструктурные здания и подъездные дороги к ним: они входят в габариты
 * ({@link #minX()}..{@link #maxY()}), но не в след ({@link #contains}).
 *
 * <p>Массивы плоские: у крупного города сотни кварталов и тысячи участков, список объектов
 * на каждый прямоугольник дал бы кратные накладные расходы.
 */
public final class Town {

    /** Квартал: прямоугольник между улицами, разбитый на участки под застройку. */
    public record Block(int x0, int y0, int x1, int y1, DistrictType type, int[] lots) {
        public int width()  { return x1 - x0 + 1; }
        public int height() { return y1 - y0 + 1; }
        public long area()  { return (long) width() * height(); }

        public int lotCount() { return lots.length / 4; }
        public int lotX0(int i) { return lots[i * 4]; }
        public int lotY0(int i) { return lots[i * 4 + 1]; }
        public int lotX1(int i) { return lots[i * 4 + 2]; }
        public int lotY1(int i) { return lots[i * 4 + 3]; }
    }

    /** Размещённое инфраструктурное здание. {@code outdoor} — вынесено за черту города. */
    public record Facility(InfraType type, int x0, int y0, int x1, int y1, boolean outdoor) {
        public int width()  { return x1 - x0 + 1; }
        public int height() { return y1 - y0 + 1; }
    }

    private final int cellX, cellY, cx, cy, halfW, halfH, attempts;
    private final Footprint shape;
    private final TownTemplate template;
    private final int[] seeds;        // {x, y, ordinal типа} * k — центры районов
    private final int[] streets;      // {x0, y0, x1, y1} * m — полосы улиц и подъездные дороги
    private final Block[] blocks;
    private final Facility[] facilities;
    private final InfraType[] missing; // что разместить не удалось

    private final int minX, minY, maxX, maxY;

    Town(int cellX, int cellY, int cx, int cy, int halfW, int halfH, Footprint shape,
         TownTemplate template, int attempts, int[] seeds, int[] streets,
         Block[] blocks, Facility[] facilities, InfraType[] missing) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.cx = cx;
        this.cy = cy;
        this.halfW = halfW;
        this.halfH = halfH;
        this.shape = shape;
        this.template = template;
        this.attempts = attempts;
        this.seeds = seeds;
        this.streets = streets;
        this.blocks = blocks;
        this.facilities = facilities;
        this.missing = missing;

        int x0 = cx - halfW, y0 = cy - halfH, x1 = cx + halfW, y1 = cy + halfH;
        for (Facility f : facilities) {
            x0 = Math.min(x0, f.x0()); y0 = Math.min(y0, f.y0());
            x1 = Math.max(x1, f.x1()); y1 = Math.max(y1, f.y1());
        }
        for (int i = 0; i < streets.length; i += 4) {
            x0 = Math.min(x0, streets[i]);     y0 = Math.min(y0, streets[i + 1]);
            x1 = Math.max(x1, streets[i + 2]); y1 = Math.max(y1, streets[i + 3]);
        }
        this.minX = x0; this.minY = y0; this.maxX = x1; this.maxY = y1;
    }

    public int cellX()  { return cellX; }
    public int cellY()  { return cellY; }
    public int cx()     { return cx; }
    public int cy()     { return cy; }
    public int halfW()  { return halfW; }
    public int halfH()  { return halfH; }
    public Footprint shape() { return shape; }
    public TownTemplate template() { return template; }
    /** Сколько попыток размещения потребовалось (1 — встал сразу). */
    public int attempts() { return attempts; }

    /** Габариты вместе с вынесенной за черту инфраструктурой. */
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }

    public boolean intersects(int x0, int y0, int x1, int y1) {
        return maxX >= x0 && minX <= x1 && maxY >= y0 && minY <= y1;
    }

    /** Точка внутри следа города (без вынесенной инфраструктуры). */
    public boolean contains(int wx, int wy) {
        return shape.contains(cx, cy, halfW, halfH, wx, wy);
    }

    public int districtCount() { return seeds.length / 3; }
    public int districtX(int i) { return seeds[i * 3]; }
    public int districtY(int i) { return seeds[i * 3 + 1]; }
    public DistrictType districtType(int i) { return DistrictType.byOrdinal(seeds[i * 3 + 2]); }

    /**
     * Ближайший центр района в метрике Чебышёва: границы районов идут по осям, как того
     * требует тайловый мир. Возвращает индекс центра, а не его тип.
     */
    static int nearestIndex(int[] seeds, int wx, int wy) {
        int best = 0;
        long bd = Long.MAX_VALUE;
        for (int i = 0; i < seeds.length; i += 3) {
            long d = Math.max(Math.abs((long) wx - seeds[i]), Math.abs((long) wy - seeds[i + 1]));
            if (d < bd) { bd = d; best = i / 3; }
        }
        return best;
    }

    /** Второй по близости центр — по нему ищутся пограничные кварталы при подгонке долей. */
    static int secondNearestIndex(int[] seeds, int wx, int wy) {
        int k = seeds.length / 3;
        if (k < 2) return 0;
        int best = -1, second = -1;
        long bd = Long.MAX_VALUE, sd = Long.MAX_VALUE;
        for (int i = 0; i < k; i++) {
            long d = Math.max(Math.abs((long) wx - seeds[i * 3]), Math.abs((long) wy - seeds[i * 3 + 1]));
            if (d < bd) { sd = bd; second = best; bd = d; best = i; }
            else if (d < sd) { sd = d; second = i; }
        }
        return second < 0 ? best : second;
    }

    public int streetCount() { return streets.length / 4; }
    public int streetX0(int i) { return streets[i * 4]; }
    public int streetY0(int i) { return streets[i * 4 + 1]; }
    public int streetX1(int i) { return streets[i * 4 + 2]; }
    public int streetY1(int i) { return streets[i * 4 + 3]; }

    /** Ширина полосы улицы в px: короткая сторона прямоугольника. */
    public int streetWidth(int i) {
        return Math.min(streetX1(i) - streetX0(i) + 1, streetY1(i) - streetY0(i) + 1);
    }

    public Block[] blocks() { return blocks; }
    public Facility[] facilities() { return facilities; }
    /** Здания, которые не удалось разместить ни в городе, ни рядом. */
    public InfraType[] missingInfra() { return missing; }

    public int lotCount() {
        int n = 0;
        for (Block b : blocks) n += b.lotCount();
        return n;
    }

    /** Фактическая доля района по площади кварталов — для проверки попадания в допуск. */
    public double districtShare(DistrictType d) {
        long total = 0, own = 0;
        for (Block b : blocks) {
            total += b.area();
            if (b.type() == d) own += b.area();
        }
        return total == 0 ? 0 : own / (double) total;
    }

    /** Максимальное отклонение фактических долей от целевых по шаблону. */
    public double worstShareError() {
        double worst = 0;
        for (DistrictType d : DistrictType.values())
            worst = Math.max(worst, Math.abs(districtShare(d) - template.share(d)));
        return worst;
    }

    @Override public String toString() {
        return template.label + " (" + cx + "," + cy + ") " + shape.label
                + " " + (2 * halfW) + "x" + (2 * halfH)
                + ", кварталов " + blocks.length + ", участков " + lotCount()
                + ", объектов " + facilities.length + ", попыток " + attempts;
    }
    
    /**
     * AI: инвариант размещения — объекты не накладываются друг на друга и ни один не стоит
     * на дороге. Проверка с нулевым зазором: генератор держит {@link TownField#OUTSIDE_CLEARANCE}
     * и {@link TownField#ROAD_CLEARANCE}, так что запас должен быть.
     *
     * @return описание первого нарушения или null, если всё чисто
     */
    public String overlapProblem() {
        for (int i = 0; i < facilities.length; i++) {
            Facility a = facilities[i];
            for (int j = i + 1; j < facilities.length; j++) {
                Facility b = facilities[j];
                if (a.x0() <= b.x1() && b.x0() <= a.x1() && a.y0() <= b.y1() && b.y0() <= a.y1())
                    return "объекты «" + a.type().label + "» и «" + b.type().label
                            + "» наложились у (" + a.x0() + "," + a.y0() + ")";
            }
            for (int s = 0; s < streetCount(); s++)
                if (a.x0() <= streetX1(s) && streetX0(s) <= a.x1()
                        && a.y0() <= streetY1(s) && streetY0(s) <= a.y1())
                    return "объект «" + a.type().label + "» стоит на дороге у ("
                            + a.x0() + "," + a.y0() + ")";
        }
        return null;
    }
}
