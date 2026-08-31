package mapgen;

import mapgen.buildings.BuildingCatalog;
import mapgen.buildings.BuildingDef;
import mapgen.buildings.BuildingZone;
import mapgen.buildings.ZoneMix;
import mapgen.towns.DistrictType;
import mapgen.towns.InfraType;

import java.nio.file.Path;
import java.util.Random;

/**
 * AI: Проверки пула построек без запуска генерации карты: что манифест читается, что под
 * характерные размеры участков каждого района вообще что-то находится, что инфраструктура
 * закрыта, и что выбор детерминирован.
 *
 * <p>Главный вопрос, на который отвечает тест, — «сколько участков останется пустыми». Пустой
 * участок это не ошибка (в пуле может не быть постройки нужного габарита), но если у жилого
 * района пустует половина, значит сетка нарезки и пул разошлись по размерам.
 *
 * <p>java -cp out mapgen.BuildingSelfTest &lt;building_pool.json&gt; [seed]
 */
public final class BuildingSelfTest {

    /** Характерные размеры участков: {@code lotSide} районов плюс мелочь по краям. */
    private static final int[] LOT_SIDES = {8, 12, 16, 20, 26, 34, 40, 46, 62, 70, 96};

    public static void main(String[] args) throws Exception {
        // AI: как в TbxRotator — иначе кириллица в отчёте зависит от кодовой страницы консоли.
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),
                true, java.nio.charset.StandardCharsets.UTF_8));
        if (args.length < 1) {
            System.out.println("usage: java -cp out mapgen.BuildingSelfTest <building_pool.json> [seed]");
            return;
        }
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1;
        BuildingCatalog catalog = BuildingCatalog.load(Path.of(args[0]));
        System.out.println(catalog.summary());

        int emptyZones = 0;
        for (BuildingZone z : BuildingZone.values()) {
            if (catalog.zone(z).isEmpty()) {
                System.out.println("ПУСТАЯ ЗОНА: " + z);
                emptyZones++;
            }
        }

        System.out.println();
        System.out.println("покрытие участков по районам (доля участков, под которые есть постройка):");
        for (DistrictType d : DistrictType.values()) {
            if (!d.buildable()) continue;
            StringBuilder row = new StringBuilder(String.format("  %-14s", d.label));
            for (int side : LOT_SIDES) {
                int hit = 0, tries = 200;
                Random rnd = new Random(seed ^ (d.ordinal() * 0x9E3779B9L) ^ side);
                for (int i = 0; i < tries; i++) {
                    BuildingZone zone = ZoneMix.zoneFor(d, rnd);
                    if (catalog.zone(zone).pick(side, side, rnd, null, 1.0) != null) hit++;
                }
                row.append(String.format(" %2d:%3.0f%%", side, 100.0 * hit / tries));
            }
            System.out.println(row);
        }

        System.out.println();
        System.out.println("инфраструктура:");
        int uncovered = 0;
        for (InfraType t : InfraType.values()) {
            if (!t.available) {
                System.out.printf("  %-22s отключён — постройки в пуле нет%n", t.label);
                continue;
            }
            BuildingZone zone = ZoneMix.zoneFor(t);
            String role = ZoneMix.roleFor(t);
            BuildingCatalog.Pool pool = catalog.zone(zone);
            BuildingCatalog.Pool byRole = role == null ? null : pool.role(role);
            if (byRole != null && !byRole.isEmpty()) pool = byRole;
            BuildingDef best = pool.largestFitting(t.minW, t.minH);
            if (best == null) {
                System.out.printf("  %-22s НЕ ЗАКРЫТ: в %s%s нет постройки до %dx%d%n",
                        t.label, zone, role == null ? "" : "/" + role, t.minW, t.minH);
                uncovered++;
            } else {
                System.out.printf("  %-22s %s %dx%d (%s)%n",
                        t.label, best.baseName(), best.w(), best.h(), zone);
            }
        }

        // AI: детерминизм — тот же seed участка обязан дать ту же постройку и тот же поворот.
        BuildingCatalog twin = BuildingCatalog.load(Path.of(args[0]));
        int mismatches = 0;
        for (int i = 0; i < 5000; i++) {
            int x = i * 37, y = i * 91;
            BuildingDef a = pickAt(catalog, x, y, seed);
            BuildingDef b = pickAt(twin, x, y, seed);
            if (a == null ? b != null : !a.path().equals(b.path())) mismatches++;
        }
        System.out.println();
        System.out.println("детерминизм: " + (mismatches == 0 ? "ок" : "РАСХОЖДЕНИЙ " + mismatches));

        System.out.printf("итого: пустых зон %d, незакрытых типов инфраструктуры %d%n",
                emptyZones, uncovered);
        if (mismatches != 0) throw new AssertionError("выбор постройки недетерминирован");
    }

    /** Повторяет схему застройщика: Random от координат участка, зона, затем подбор. */
    private static BuildingDef pickAt(BuildingCatalog catalog, int x, int y, long seed) {
        long h = seed ^ ("lot".hashCode() * 0x9E3779B97F4A7C15L) ^ (x * 73856093L) ^ (y * 19349663L);
        Random rnd = new Random(h * 0xBF58476D1CE4E5B9L);
        BuildingZone zone = ZoneMix.zoneFor(DistrictType.RESIDENTIAL, rnd);
        return catalog.zone(zone).pick(20, 20, rnd, null, 1.0);
    }
}
