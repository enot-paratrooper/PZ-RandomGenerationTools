package mapgen.generators;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;
import mapgen.core.Generator;
import mapgen.core.Layer;
import mapgen.core.World;
import mapgen.towns.BuildingPlacer;
import mapgen.towns.StubBuildingPlacer;
import mapgen.towns.Town;

import java.util.List;

/**
 * Наносит города: улицы и подъездные дороги, затем застройку кварталов и инфраструктуру
 * через {@link BuildingPlacer}. Геометрия города глобальна и детерминирована
 * ({@link mapgen.towns.TownField}), поэтому улица, пересекающая шов блоков, совпадает
 * по обе стороны шва.
 *
 * <p>Проверки принадлежности следу здесь нет: улицы уже подрезаны по выжившим кварталам,
 * а кварталы отобраны при постройке города. Это заодно убирает {@code contains()} из
 * попиксельного цикла.
 *
 * <p>Идёт после {@link RiverGenerator} и до {@link VegetationGenerator}: вода уже нанесена
 * (по ней не строим), а растительность ещё нет — след города помечается
 * {@link Chunk#blockVegetation}, иначе на асфальте вырастет лес.
 *
 * <p>AI: дороги теперь асфальтовые и различаются по классу, а не «гравий против грунта».
 * Класс берётся из ширины полосы, которую заложила нарезка кварталов: {@code baseStreetWidth}
 * даёт 14 px на первых разрезах, 10 на средних и 7 на последних, всё это домножается на
 * {@code streetScale} района. Так магистраль остаётся магистралью и на промышленной окраине,
 * где полосы шире, и в тесном центре, где уже.
 *
 * <p>Полей, кроме неизменяемого застройщика, нет — экземпляр разделяется потоками.
 */
public final class TownGenerator implements Generator {

    /** AI: с этой ширины полоса — магистраль (street2, самый тёмный асфальт). */
    private static final int HIGHWAY_WIDTH = 12;

    /** AI: с этой — обычная улица (street). Всё, что уже, считается проездом или подъездом. */
    private static final int STREET_WIDTH = 7;

    /** AI: рисовать ли износ полотна. Выключается одним флагом, если понадобится чистый асфальт. */
    private static final boolean POTHOLES = true;

    /**
     * AI: пороги износа по полю {@link World#patch}. Поле — чистая функция мировых координат,
     * поэтому пятна не рвутся на швах блоков и не зависят ни от числа потоков, ни от порядка
     * обхода. Оно же лепит пятна земли в {@link BaseSurfaceGenerator}, так что затёртый асфальт
     * ложится там же, где рядом проступает грунт, — это скорее плюс.
     */
    private static final double WORN_LEVEL = 0.80, POTHOLE_LEVEL = 0.88;

    private final BuildingPlacer placer;

    public TownGenerator() { this(new StubBuildingPlacer()); }

    public TownGenerator(BuildingPlacer placer) { this.placer = placer; }

    @Override public String name() { return "towns"; }

    @Override
    public void generate(GenContext ctx, Chunk chunk) {
        int ox = chunk.worldX(0), oy = chunk.worldY(0);
        int mx = ox + chunk.size - 1, my = oy + chunk.size - 1;
        List<Town> towns = ctx.towns().intersecting(ox, oy, mx, my);
        if (towns.isEmpty()) return;

        World world = ctx.world();
        Palette p = world.palette();
        Layer base = chunk.base();

        for (Town t : towns) {
            for (int i = 0; i < t.streetCount(); i++) {
                int x0 = Math.max(ox, t.streetX0(i)), x1 = Math.min(mx, t.streetX1(i));
                int y0 = Math.max(oy, t.streetY0(i)), y1 = Math.min(my, t.streetY1(i));
                if (x0 > x1 || y0 > y1) continue;
                int surface = roadSurface(p, t.streetWidth(i));
                for (int wy = y0; wy <= y1; wy++) {
                    for (int wx = x0; wx <= x1; wx++) {
                        if (ctx.isWater(wx, wy)) continue;      // мостов пока нет
                        int x = wx - ox, y = wy - oy;
                        base.set(x, y, wear(p, world, surface, wx, wy));
                        chunk.blockVegetation(x, y);
                    }
                }
            }
            for (Town.Block b : t.blocks()) {
                if (b.x1() < ox || b.x0() > mx || b.y1() < oy || b.y0() > my) continue;
                placer.place(ctx, chunk, t, b);
            }
            for (Town.Facility f : t.facilities()) {
                if (f.x1() < ox || f.x0() > mx || f.y1() < oy || f.y0() > my) continue;
                placer.placeFacility(ctx, chunk, t, f);
            }
        }
    }

    /** AI: класс дороги по ширине полосы. */
    private static int roadSurface(Palette p, int width) {
        if (width >= HIGHWAY_WIDTH) return p.darkAsphalt;
        if (width >= STREET_WIDTH)  return p.mediumAsphalt;
        return p.lightAsphalt;
    }

    /**
     * AI: износ полотна. Обочины и подъезды (lightgravel) не затираем — на них выбоины
     * читались бы как грязь, а не как разбитый асфальт.
     */
    private static int wear(Palette p, World world, int surface, int wx, int wy) {
        if (!POTHOLES || surface == p.lightAsphalt) return surface;
        double d = world.patch(wx, wy);
        if (d > POTHOLE_LEVEL) return p.lightPothole;
        if (d > WORN_LEVEL)    return p.darkPothole;
        return surface;
    }
}
