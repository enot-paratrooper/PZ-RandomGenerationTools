package mapgen.generators;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.Generator;
import mapgen.core.Layer;
import mapgen.core.World;

/**
 * Наносит реки из RiverNetwork на блок и рисует песчаные берега вокруг любой воды.
 * Проверка соседей идёт по мировым координатам, поэтому берега на стыках блоков совпадают.
 */
public final class RiverGenerator implements Generator {
    @Override public String name() { return "Rivers"; }

    @Override
    public void generate(World world, Chunk chunk) {
        world.rivers().ensureTracedAround(chunk.cx, chunk.cy);
        Palette p = world.palette();
        Layer base = chunk.base();

        for (int y = 0; y < chunk.size; y++) {
            for (int x = 0; x < chunk.size; x++) {
                int wx = chunk.worldX(x), wy = chunk.worldY(y);
                if (world.rivers().isWater(wx, wy)) {
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
                        if ((dx != 0 || dy != 0) && world.isWater(wx + dx, wy + dy)) { shore = true; break; }
                if (shore) base.set(x, y, p.sand);
            }
        }
    }
}
