package mapgen.generators;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;
import mapgen.core.Generator;
import mapgen.core.Layer;
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
 * <p>Полей, кроме неизменяемого застройщика, нет — экземпляр разделяется потоками.
 */
public final class TownGenerator implements Generator {

    /** С этой ширины улица считается магистралью и рисуется гравием, а не грунтом. */
    private static final int WIDE_STREET = 10;

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

        Palette p = ctx.world().palette();
        Layer base = chunk.base();

        for (Town t : towns) {
            for (int i = 0; i < t.streetCount(); i++) {
                int x0 = Math.max(ox, t.streetX0(i)), x1 = Math.min(mx, t.streetX1(i));
                int y0 = Math.max(oy, t.streetY0(i)), y1 = Math.min(my, t.streetY1(i));
                if (x0 > x1 || y0 > y1) continue;
                int color = t.streetWidth(i) >= WIDE_STREET ? p.gravelDirt : p.dirt;
                for (int wy = y0; wy <= y1; wy++) {
                    for (int wx = x0; wx <= x1; wx++) {
                        if (ctx.isWater(wx, wy)) continue;      // мостов пока нет
                        int x = wx - ox, y = wy - oy;
                        base.set(x, y, color);
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
}
