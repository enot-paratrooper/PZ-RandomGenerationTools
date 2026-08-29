package mapgen.io;

import mapgen.core.World;
import mapgen.core.WorldState;
import mapgen.rivers.River;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Отладочная картинка: серый = высота, тёмно-синий = природные озёра, голубой = реки,
 * красный = устья, зелёный = озёра-окончания, жёлтые линии = границы блоков,
 * затемнение = блок ещё не сгенерирован.
 */
public final class RiverDebugExporter {
    private RiverDebugExporter() {}

    public static void export(World world, Path file) throws IOException {
        WorldState state = world.state();
        if (state.generatedChunks.isEmpty()) return;
        int[] bb = ChunkStore.bounds(state);
        int cs = World.CHUNK_SIZE;
        int ox = bb[0] * cs, oy = bb[1] * cs;
        int w = (bb[2] - bb[0] + 1) * cs, h = (bb[3] - bb[1] + 1) * cs;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int wx = ox + x, wy = oy + y;
                float e = world.height(wx, wy);
                int g = (int) (40 + 215 * e);
                int rgb;
                if (world.isLake(wx, wy))               rgb = 0x103080;
                else if (world.rivers().isWater(wx, wy)) rgb = 0x30A0FF;
                else                                    rgb = (g << 16) | (g << 8) | g;
                boolean generated = state.generatedChunks.contains(
                        WorldState.key(Math.floorDiv(wx, cs), Math.floorDiv(wy, cs)));
                if (!generated) rgb = (rgb >> 1) & 0x7F7F7F;
                if (x % cs == 0 || y % cs == 0) rgb = 0xFFFF00;
                img.setRGB(x, y, rgb);
            }
        }
        for (River r : state.rivers) {
            dot(img, r.sourceX() - ox, r.sourceY() - oy, 3, 0xFF0000);
            if (r.lakeRadius() > 0) {
                int[] e = r.path().get(r.path().size() - 1);
                dot(img, e[0] - ox, e[1] - oy, 2, 0x00FF00);
            }
        }
        ImageIO.write(img, "bmp", file.toFile());
    }

    private static void dot(BufferedImage img, int cx, int cy, int r, int rgb) {
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, rgb);
            }
    }
}
