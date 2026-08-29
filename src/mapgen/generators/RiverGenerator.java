package mapgen.generators;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;
import mapgen.core.Generator;
import mapgen.core.Layer;

/**
 * Наносит воду из замороженной маски на блок и рисует песчаные берега вокруг любой воды.
 * Проверка соседей идёт по мировым координатам, поэтому берега на стыках блоков совпадают;
 * высота для этих проверок берётся из окна с каймой в GenContext, а не пересчитывается
 * восемь раз на пиксель.
 *
 * <p>Трассировки здесь больше нет — она целиком уходит в фазу 1 (RiverPlanner).
 */
public final class RiverGenerator implements Generator {
    @Override public String name() { return "rivers"; }

    @Override
    public void generate(GenContext ctx, Chunk chunk) {
        Palette p = ctx.world().palette();
        Layer base = chunk.base();

        for (int y = 0; y < chunk.size; y++) {
            for (int x = 0; x < chunk.size; x++) {
                if (ctx.isRiverWater(chunk.worldX(x), chunk.worldY(y))) {
                    base.set(x, y, p.water);
                    chunk.blockVegetation(x, y);
                }
            }
        }
        // берега
        for (int y = 0; y < chunk.size; y++) {
            for (int x = 0; x < chunk.size; x++) {
                int c = base.get(x, y);
                if (c == p.water || c == p.rocksSmall || c == p.rocksMedium || c == p.rocksLarge) continue;
                int wx = chunk.worldX(x), wy = chunk.worldY(y);
                boolean shore = false;
                for (int dy = -1; dy <= 1 && !shore; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        if ((dx != 0 || dy != 0) && ctx.isWater(wx + dx, wy + dy)) { shore = true; break; }
                if (shore) base.set(x, y, p.sand);
            }
        }
    }
}
