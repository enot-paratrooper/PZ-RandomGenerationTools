package mapgen.towns;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;

/**
 * ЗАГЛУШКА. Настоящих зданий не ставит: помечает участок как занятый (растительность
 * под будущим домом не растёт) и, если включён {@link #MARK_LOTS}, закрашивает его пятном
 * утоптанной земли — чтобы структура города была видна на карте до появления генератора зданий.
 * Инфраструктурные здания метятся более тёмным следом, чтобы отличаться от рядовой застройки.
 *
 * <p>TODO: заменить на реализацию, которая для каждого участка
 * <ol>
 *   <li>выбирает шаблон здания по {@link DistrictType} (или по {@link InfraType} для
 *       инфраструктуры) и размеру участка;</li>
 *   <li>детерминированно (Random от координат участка!) выбирает вариант и ориентацию;</li>
 *   <li>рисует след здания на базовом слое и отдаёт геометрию экспортёру .tbx.</li>
 * </ol>
 * Случайность обязана зависеть только от координат участка, иначе один и тот же дом
 * получится разным в соседних блоках карты.
 *
 * <p>Полей нет — экземпляр разделяется потоками.
 */
public final class StubBuildingPlacer implements BuildingPlacer {

    /** Рисовать след участков на карте. Выключить, когда появятся настоящие здания. */
    public static final boolean MARK_LOTS = true;

    @Override
    public void place(GenContext ctx, Chunk chunk, Town town, Town.Block block) {
        Palette p = ctx.world().palette();
        for (int i = 0; i < block.lotCount(); i++)
            mark(ctx, chunk, block.lotX0(i), block.lotY0(i), block.lotX1(i), block.lotY1(i), p.dirtGrass);
    }

    @Override
    public void placeFacility(GenContext ctx, Chunk chunk, Town town, Town.Facility f) {
        Palette p = ctx.world().palette();
        mark(ctx, chunk, f.x0(), f.y0(), f.x1(), f.y1(), p.dirt);
    }

    private static void mark(GenContext ctx, Chunk chunk, int rx0, int ry0, int rx1, int ry1, int color) {
        int ox = chunk.worldX(0), oy = chunk.worldY(0);
        int mx = ox + chunk.size - 1, my = oy + chunk.size - 1;
        int x0 = Math.max(ox, rx0), x1 = Math.min(mx, rx1);
        int y0 = Math.max(oy, ry0), y1 = Math.min(my, ry1);
        for (int wy = y0; wy <= y1; wy++)
            for (int wx = x0; wx <= x1; wx++) {
                if (ctx.isWater(wx, wy)) continue;
                int x = wx - ox, y = wy - oy;
                chunk.blockVegetation(x, y);
                if (MARK_LOTS) chunk.base().set(x, y, color);
            }
    }
}
