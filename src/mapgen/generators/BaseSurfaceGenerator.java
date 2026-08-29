package mapgen.generators;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;
import mapgen.core.Generator;
import mapgen.core.Layer;
import mapgen.core.World;

/** Базовая поверхность: озёра -> песок -> травы по влажности с пятнами земли -> камни на высотах. */
public final class BaseSurfaceGenerator implements Generator {
    @Override public String name() { return "surface"; }

    @Override
    public void generate(GenContext ctx, Chunk chunk) {
        World world = ctx.world();
        Palette p = world.palette();
        Layer base = chunk.base();
        double sea = world.seaLevel(), rock = world.rockLevel();

        for (int y = 0; y < chunk.size; y++) {
            for (int x = 0; x < chunk.size; x++) {
                int wx = chunk.worldX(x), wy = chunk.worldY(y);
                double e = ctx.height(wx, wy), m = ctx.moisture(wx, wy), d = world.patch(wx, wy);
                int color;
                if (e < sea) {
                    color = p.water;
                    chunk.blockVegetation(x, y);
                } else if (e < sea + 0.03) {
                    color = p.sand;
                } else if (e > rock) {
                    double r = (e - rock) / (1 - rock);
                    color = r > 0.6 ? p.rocksLarge : r > 0.3 ? p.rocksMedium : p.rocksSmall;
                } else if (d > 0.72 && m < 0.45) {
                    color = d > 0.8 ? p.dirt : p.dirtGrass;
                } else if (m < 0.38) {
                    color = p.lightGrass;
                } else if (m < 0.58) {
                    color = p.mediumGrass;
                } else {
                    color = p.darkGrass;
                }
                base.set(x, y, color);
            }
        }
    }
}
