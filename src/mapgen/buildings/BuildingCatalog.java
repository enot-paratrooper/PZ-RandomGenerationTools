package mapgen.buildings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * AI: Пул построек, загруженный из {@code conf/ManualBuildings/building_pool.json}.
 *
 * <p>Манифест уже содержит только уникальные записи (дубликаты между паками отсеяны на этапе
 * классификации), поэтому взвешенная выборка здесь не перекошена в пользу построек, которые
 * просто скопированы в шесть паков.
 *
 * <p>Внутри зоны записи разложены по возрастанию длинной стороны. Подбор под участок —
 * это «взять префикс, у которого длинная сторона не больше длинной стороны участка, и
 * пройтись по нему точной проверкой». Без такого индекса подбор для RESIDENTIAL_LOW означал
 * бы линейный проход по 656 записям на каждый участок города.
 *
 * <p>Что в пул <b>не</b> попадает (по результатам анализа):
 * <ul>
 *   <li>вытянутые дальше {@link #MAX_ASPECT} — эстакады, сортировочные горки, «golden gate»
 *       40x290: в квартал они не проходят никогда, а вес в выборке съедают;</li>
 *   <li>мельче {@link #MIN_SIDE} по длинной стороне — столбы ЛЭП 1x1 и указатели: это точечный
 *       декор вдоль дорог, а не лот.</li>
 * </ul>
 * Отброшенные записи остаются доступны через {@link #oversized(BuildingZone)} — под будущий
 * семенной проход по особым точкам (набережная, ж/д линия).
 *
 * <p>Объект неизменяем после загрузки и читается из всех потоков растеризации.
 */
public final class BuildingCatalog {

    /** Предел вытянутости для рядовой застройки. */
    public static final double MAX_ASPECT = 2.5;
    /** Минимальная длинная сторона: всё, что меньше, — декор, а не постройка. */
    public static final int MIN_SIDE = 3;

    /** Зона со своим индексом по размеру и разбивкой по ролям. */
    public static final class Pool {
        private final BuildingDef[] bySize;   // по возрастанию длинной стороны
        private final int[] maxSides;         // bySize[i].maxSide(), для двоичного поиска
        private final Map<String, Pool> byRole;

        private Pool(List<BuildingDef> defs, boolean withRoles) {
            List<BuildingDef> sorted = new ArrayList<>(defs);
            // AI: сравнение до полного порядка — иначе сортировка зависела бы от порядка входа,
            // а он зависит от порядка ключей в JSON. Детерминизм важнее скорости.
            sorted.sort(Comparator.comparingInt(BuildingDef::maxSide)
                    .thenComparingInt(BuildingDef::minSide)
                    .thenComparing(BuildingDef::path));
            this.bySize = sorted.toArray(new BuildingDef[0]);
            this.maxSides = new int[bySize.length];
            for (int i = 0; i < bySize.length; i++) maxSides[i] = bySize[i].maxSide();

            if (!withRoles) {
                this.byRole = Map.of();
            } else {
                Map<String, List<BuildingDef>> groups = new HashMap<>();
                for (BuildingDef d : sorted) groups.computeIfAbsent(d.role(), k -> new ArrayList<>()).add(d);
                Map<String, Pool> roles = new HashMap<>();
                groups.forEach((role, list) -> roles.put(role, new Pool(list, false)));
                this.byRole = Map.copyOf(roles);
            }
        }

        public int size() { return bySize.length; }
        public boolean isEmpty() { return bySize.length == 0; }
        public BuildingDef get(int i) { return bySize[i]; }

        /** Подпул одной роли или {@code null}, если роли в зоне нет. */
        public Pool role(String role) { return byRole.get(role); }

        /**
         * Число записей, у которых длинная сторона не больше {@code side}. Это верхняя граница
         * префикса, дальше которого точная проверка заведомо провалится.
         */
        public int upperBound(int side) {
            int lo = 0, hi = maxSides.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (maxSides[mid] <= side) lo = mid + 1;
                else hi = mid;
            }
            return lo;
        }

        /** Самая крупная постройка, влезающая в участок, или {@code null}. */
        public BuildingDef largestFitting(int lotW, int lotH) {
            for (int i = upperBound(Math.max(lotW, lotH)) - 1; i >= 0; i--)
                if (bySize[i].fits(lotW, lotH)) return bySize[i];
            return null;
        }

        /**
         * Взвешенный выбор подходящей постройки: вес — площадь, поэтому участок заполняется,
         * а не занимается сараем; постройка из «недавнего» семейства получает
         * {@code familyPenalty}-кратный вес, чтобы серия не вставала подряд.
         *
         * <p>Выборка — один проход методом резервуара: без промежуточного списка кандидатов
         * и без второго прохода по префиксу. Число обращений к {@code rnd} зависит только от
         * размеров участка, а {@code rnd} создаётся на участок, поэтому результат детерминирован.
         */
        public BuildingDef pick(int lotW, int lotH, Random rnd, String[] recentFamilies,
                                double familyPenalty) {
            int end = upperBound(Math.max(lotW, lotH));
            BuildingDef chosen = null;
            double total = 0;
            for (int i = 0; i < end; i++) {
                BuildingDef d = bySize[i];
                if (!d.fits(lotW, lotH)) continue;
                double weight = d.area();
                if (contains(recentFamilies, d.family())) weight *= familyPenalty;
                total += weight;
                if (rnd.nextDouble() * total < weight) chosen = d;
            }
            return chosen;
        }

        private static boolean contains(String[] families, String family) {
            if (families == null || family == null || family.isEmpty()) return false;
            for (String f : families) if (family.equals(f)) return true;
            return false;
        }
    }

    private static final Pool EMPTY = new Pool(List.of(), true);

    private final Map<BuildingZone, Pool> zones = new EnumMap<>(BuildingZone.class);
    private final Map<BuildingZone, Pool> oversized = new EnumMap<>(BuildingZone.class);
    private final int total, dropped;

    private BuildingCatalog(Map<BuildingZone, List<BuildingDef>> kept,
                            Map<BuildingZone, List<BuildingDef>> skipped,
                            int total, int dropped) {
        for (BuildingZone z : BuildingZone.values()) {
            zones.put(z, new Pool(kept.getOrDefault(z, List.of()), true));
            oversized.put(z, new Pool(skipped.getOrDefault(z, List.of()), true));
        }
        this.total = total;
        this.dropped = dropped;
    }

    /**
     * Читает манифест. Верхний уровень — объект «зона -> {entries: [...]}»; всё остальное
     * (счётчики, распределения) игнорируется: это статистика отчёта, а не входные данные.
     */
    public static BuildingCatalog load(Path json) throws IOException {
        Json root = Json.parse(Files.readString(json, StandardCharsets.UTF_8));
        Map<BuildingZone, List<BuildingDef>> kept = new EnumMap<>(BuildingZone.class);
        Map<BuildingZone, List<BuildingDef>> skipped = new EnumMap<>(BuildingZone.class);
        int total = 0, dropped = 0;

        for (String key : root.keys()) {
            BuildingZone zone = BuildingZone.byKey(key);
            if (zone == null) {
                System.err.println("ПУЛ ПОСТРОЕК: незнакомая зона «" + key + "» пропущена");
                continue;
            }
            Json entries = root.get(key).get("entries");
            if (entries == null) continue;
            for (Json e : entries.items()) {
                String path = e.str("p", "").replace('\\', '/');
                int w = e.num("w", 0), h = e.num("h", 0);
                if (path.isEmpty() || w <= 0 || h <= 0) continue;
                String facing = e.str("f", "");
                BuildingDef def = new BuildingDef(path, w, h,
                        e.str("t", "M"), e.str("r", ""),
                        facing.isEmpty() ? '?' : facing.charAt(0),
                        e.str("g", ""), e.str("c", ""), zone);
                total++;
                if (def.maxSide() < MIN_SIDE || def.aspect() > MAX_ASPECT) {
                    skipped.computeIfAbsent(zone, k -> new ArrayList<>()).add(def);
                    dropped++;
                } else {
                    kept.computeIfAbsent(zone, k -> new ArrayList<>()).add(def);
                }
            }
        }
        if (total == 0) throw new IllegalArgumentException("в манифесте нет ни одной постройки: " + json);
        return new BuildingCatalog(kept, skipped, total, dropped);
    }

    /** Пул зоны; никогда не {@code null}, но может быть пустым. */
    public Pool zone(BuildingZone z) { return zones.getOrDefault(z, EMPTY); }

    /** Отсеянные из общего пула: вытянутые и точечный декор. */
    public Pool oversized(BuildingZone z) { return oversized.getOrDefault(z, EMPTY); }

    /** Сколько записей прочитано всего. */
    public int total() { return total; }

    /** Сколько из них не годится для рядовой застройки. */
    public int dropped() { return dropped; }

    /** Строка для лога запуска. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("пул построек: ").append(total - dropped).append(" из ").append(total)
          .append(" (отсеяно вытянутых и декора ").append(dropped).append(')');
        for (BuildingZone z : BuildingZone.values()) {
            Pool p = zone(z);
            if (!p.isEmpty()) sb.append("\n   ").append(z).append(' ').append(p.size());
        }
        return sb.toString();
    }

    /** Диапазон размеров зоны — для самопроверки. */
    public int[] sizeRange(BuildingZone z) {
        Pool p = zone(z);
        if (p.isEmpty()) return new int[]{0, 0};
        return new int[]{p.get(0).maxSide(), p.get(p.size() - 1).maxSide()};
    }

    @Override public String toString() {
        return "BuildingCatalog[" + (total - dropped) + " построек, зон "
                + Arrays.stream(BuildingZone.values()).filter(z -> !zone(z).isEmpty()).count() + "]";
    }
}
