package mapgen.io;

import mapgen.core.World;
import mapgen.core.WorldState;
import mapgen.rivers.River;
import mapgen.rivers.WaterMask;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.IntStream;

/**
 * Отладочная картинка: серый = высота, тёмно-синий = природные озёра, голубой = реки,
 * красный = устья, жёлтые линии = границы блоков, затемнение = блок ещё не сгенерирован.
 * Сжатые реки видны как ломаные из отрезков — по ним удобно проверять качество упрощения.
 *
 * <p>Строки считаются параллельно: каждая полоса получает собственный WaterMask.View,
 * пишет в непересекающиеся пиксели BufferedImage.
 */
public final class RiverDebugExporter {
    private RiverDebugExporter() {}

    public static void export(World world, WorldState state, WaterMask mask, Path file) throws IOException {
        if (state.generatedChunks.isEmpty()) return;
        int[] bb = ChunkStore.bounds(state);
        int cs = World.CHUNK_SIZE;
        int ox = bb[0] * cs, oy = bb[1] * cs;
        int w = (bb[2] - bb[0] + 1) * cs, h = (bb[3] - bb[1] + 1) * cs;
        if (!ChunkStore.fits(w, h, 1)) {
            System.err.printf("ОТЛАДОЧНАЯ КАРТА пропущена: габарит %dx%d px не влезает в кучу.%n", w, h);
            return;
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        int bands = Math.min(Runtime.getRuntime().availableProcessors(), Math.max(1, h / 64));
        int bandH = (h + bands - 1) / bands;
        IntStream.range(0, bands).parallel().forEach(band -> {
            WaterMask.View view = mask.view();
            int y0 = band * bandH, y1 = Math.min(h, y0 + bandH);
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < w; x++) {
                    int wx = ox + x, wy = oy + y;
                    float e = world.height(wx, wy);
                    int g = (int) (40 + 215 * e);
                    int rgb;
                    if (world.isLake(wx, wy))     rgb = 0x103080;
                    else if (view.isWater(wx, wy)) rgb = 0x30A0FF;
                    else                           rgb = (g << 16) | (g << 8) | g;
                    boolean generated = state.generatedChunks.contains(
                            WorldState.key(Math.floorDiv(wx, cs), Math.floorDiv(wy, cs)));
                    if (!generated) rgb = (rgb >> 1) & 0x7F7F7F;
                    if (x % cs == 0 || y % cs == 0) rgb = 0xFFFF00;
                    img.setRGB(x, y, rgb);
                }
            }
        });

        for (River r : state.rivers) dot(img, r.mouthX() - ox, r.mouthY() - oy, 3, 0xFF0000);
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
