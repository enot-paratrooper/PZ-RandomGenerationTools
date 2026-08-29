package mapgen.generators;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenContext;
import mapgen.core.Generator;
import mapgen.core.Layer;
import mapgen.core.World;

import java.util.Random;

/**
 * Второй слой. Если клетка помечена blockVegetation (вода, позже — дороги/здания) — чёрный.
 * Иначе лес / кусты / трава по полям лесистости и влажности с учётом типа поверхности.
 *
 * <p>RNG засеян координатами блока (World.random), поэтому результат не зависит ни от порядка
 * обхода блоков, ни от числа потоков.
 */
public final class VegetationGenerator implements Generator {
    @Override public String name() { return "veg"; }

    @Override
    public void generate(GenContext ctx, Chunk chunk) {
        World world = ctx.world();
        Palette p = world.palette();
        Layer base = chunk.base(), veg = chunk.vegetation();
        Random rnd = world.random("vegetation", chunk.cx, chunk.cy);

        for (int y = 0; y < chunk.size; y++) {
            for (int x = 0; x < chunk.size; x++) {
                double jitter = rnd.nextDouble() * 0.05;   // вызываем всегда, чтобы RNG-поток не зависел от ветвлений
                if (chunk.isVegetationBlocked(x, y)) { veg.set(x, y, p.none); continue; }

                int wx = chunk.worldX(x), wy = chunk.worldY(y);
                int surface = base.get(x, y);
                double f = world.forest(wx, wy) + jitter;
                double b = world.bush(wx, wy);
                double m = ctx.moisture(wx, wy);
                int color;

                if (p.isGrass(surface)) {
                    if (f > 0.70)       color = p.denseTrees;
                    else if (f > 0.62)  color = p.trees;
                    else if (f > 0.55)  color = ctx.height(wx, wy) > 0.65 ? p.firTrees : p.treesGrass;
                    else if (f > 0.48)  color = p.grassFewTrees;
                    else if (b > 0.66)  color = p.denseBushes;
                    else if (b > 0.60)  color = p.bushes;
                    else if (b > 0.55)  color = p.bushesFewTrees;
                    else if (b > 0.50)  color = p.sparseBushes;
                    else                color = m > 0.5 ? p.longGrass : p.shortGrass;
                } else if (surface == p.dirtGrass) {
                    color = b > 0.6 ? p.sparseBushes : p.shortGrass;
                } else if (surface == p.sand) {
                    color = (b > 0.68 && jitter < 0.015) ? p.sparseBushes : p.none;
                } else {
                    color = p.none;
                }
                veg.set(x, y, color);
            }
        }
    }
}
