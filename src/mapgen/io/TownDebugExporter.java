package mapgen.io;

import mapgen.core.World;
import mapgen.core.WorldState;
import mapgen.rivers.WaterMask;
import mapgen.towns.DistrictType;
import mapgen.towns.InfraType;
import mapgen.towns.Town;
import mapgen.towns.TownIndex;
import mapgen.towns.TownTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Отдельная карта городов: приглушённый рельеф с водой на фоне, поверх — кварталы в цвете
 * своего района, участки, улицы, инфраструктурные здания и кромка следа.
 *
 * <p>Цвета районов берутся из {@link DistrictType}: жилой зелёный, коммерческий синий,
 * промышленный жёлтый, офисный голубой, военный красный, плюс центр (оранжевый),
 * парк (тёмно-зелёный) и ферма (коричневая). Инфраструктура рисуется своими цветами
 * из {@link InfraType} с тёмной окантовкой; вынесенные за черту объекты видны на конце
 * подъездных дорог.
 *
 * <p>Фон считается параллельно полосами (у каждой свой {@link WaterMask.View}), геометрия
 * городов дорисовывается одним потоком прямоугольниками.
 */
public final class TownDebugExporter {
    private TownDebugExporter() {}

    private static final int STREET_RGB  = 0x282828;
    private static final int BORDER_RGB  = 0x101010;
    private static final int OUTLINE_RGB = 0xFFFFFF;
    private static final int LAKE_RGB    = 0x0A2050;
    private static final int RIVER_RGB   = 0x1A4A80;
    /** Насколько притушить заливку квартала, чтобы участки читались поверх неё. */
    private static final double BLOCK_DIM = 0.45;

    public static void export(World world, WorldState state, WaterMask mask, Path file) throws IOException {
        if (state.generatedChunks.isEmpty()) return;
        int[] bb = ChunkStore.bounds(state);
        int cs = World.CHUNK_SIZE;
        int ox = bb[0] * cs, oy = bb[1] * cs;
        int w = (bb[2] - bb[0] + 1) * cs, h = (bb[3] - bb[1] + 1) * cs;
        if (!ChunkStore.fits(w, h, 1)) {
            System.err.printf("КАРТА ГОРОДОВ пропущена: габарит %dx%d px не влезает в кучу.%n", w, h);
            return;
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // ---------- фон: рельеф и вода, приглушённые ----------
        int bands = Math.min(Runtime.getRuntime().availableProcessors(), Math.max(1, h / 64));
        int bandH = (h + bands - 1) / bands;
        IntStream.range(0, bands).parallel().forEach(band -> {
            WaterMask.View view = mask.view();
            int y0 = band * bandH, y1 = Math.min(h, y0 + bandH);
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < w; x++) {
                    int wx = ox + x, wy = oy + y;
                    int rgb;
                    if (world.isLake(wx, wy))       rgb = LAKE_RGB;
                    else if (view.isWater(wx, wy))  rgb = RIVER_RGB;
                    else {
                        int g = (int) (30 + 90 * world.height(wx, wy));
                        rgb = (g << 16) | (g << 8) | g;
                    }
                    img.setRGB(x, y, rgb);
                }
            }
        });

        // ---------- города ----------
        TownIndex index = new TownIndex(world.townField());
        List<Town> towns = index.intersecting(ox, oy, ox + w - 1, oy + h - 1);
        int lots = 0, blocks = 0, attempts = 0, facilities = 0, missing = 0;
        int[] perType = new int[DistrictType.count()];
        int[] perTemplate = new int[TownTemplate.values().length];
        double worstShare = 0;

        for (Town t : towns) {
            for (Town.Block b : t.blocks()) {
                fill(img, ox, oy, b.x0(), b.y0(), b.x1(), b.y1(), dim(b.type().color, BLOCK_DIM));
                for (int i = 0; i < b.lotCount(); i++)
                    fill(img, ox, oy, b.lotX0(i), b.lotY0(i), b.lotX1(i), b.lotY1(i), b.type().color);
                perType[b.type().ordinal()]++;
                lots += b.lotCount();
            }
            for (int i = 0; i < t.streetCount(); i++)
                fill(img, ox, oy, t.streetX0(i), t.streetY0(i), t.streetX1(i), t.streetY1(i), STREET_RGB);
            for (Town.Facility f : t.facilities()) {
                fill(img, ox, oy, f.x0() - 1, f.y0() - 1, f.x1() + 1, f.y1() + 1, BORDER_RGB);
                fill(img, ox, oy, f.x0(), f.y0(), f.x1(), f.y1(), f.type().color);
            }
            outline(img, ox, oy, t);
            dot(img, t.cx() - ox, t.cy() - oy, 4, OUTLINE_RGB);
            blocks += t.blocks().length;
            attempts += t.attempts();
            facilities += t.facilities().length;
            missing += t.missingInfra().length;
            perTemplate[t.template().ordinal()]++;
            worstShare = Math.max(worstShare, t.worstShareError());
        }

        ImageIO.write(img, "bmp", file.toFile());

        System.out.printf("Города: %d (кварталов %d, участков %d, объектов %d, "
                        + "попыток размещения в среднем %.1f)%n",
                towns.size(), blocks, lots, facilities,
                towns.isEmpty() ? 0 : attempts / (double) towns.size());
        if (towns.isEmpty()) return;
        System.out.printf("  худшее отклонение доли района: %.1f%% (допуск %.1f%%), "
                        + "не размещено объектов: %d%n",
                100 * worstShare, 100 * mapgen.towns.TownField.SHARE_TOLERANCE, missing);
        List<String> tpl = new ArrayList<>();
        for (TownTemplate t : TownTemplate.values())
            if (perTemplate[t.ordinal()] > 0) tpl.add(t.label + " " + perTemplate[t.ordinal()]);
        System.out.println("  шаблоны: " + String.join(", ", tpl));
        List<String> legend = new ArrayList<>();
        for (DistrictType d : DistrictType.values())
            if (perType[d.ordinal()] > 0)
                legend.add(String.format("#%06X %s (%d)", d.color, d.label, perType[d.ordinal()]));
        System.out.println("  районы: " + String.join(", ", legend));
    }

    /** Заливка прямоугольника с обрезкой по картинке. */
    private static void fill(BufferedImage img, int ox, int oy,
                             int x0, int y0, int x1, int y1, int rgb) {
        int px0 = Math.max(0, x0 - ox), px1 = Math.min(img.getWidth() - 1, x1 - ox);
        int py0 = Math.max(0, y0 - oy), py1 = Math.min(img.getHeight() - 1, y1 - oy);
        for (int y = py0; y <= py1; y++)
            for (int x = px0; x <= px1; x++) img.setRGB(x, y, rgb);
    }

    /** Кромка следа: обход по параметру формы, поэтому годится и для прямоугольника. */
    private static void outline(BufferedImage img, int ox, int oy, Town t) {
        int steps = Math.max(128, Math.max(t.halfW(), t.halfH()) * 6);
        for (int i = 0; i < steps; i++) {
            double a = 2 * Math.PI * i / steps;
            double[] p = t.shape().ray(t.cx(), t.cy(), t.halfW(), t.halfH(), a, 1.0);
            int x = (int) Math.round(p[0]) - ox, y = (int) Math.round(p[1]) - oy;
            if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, OUTLINE_RGB);
        }
    }

    private static void dot(BufferedImage img, int cx, int cy, int r, int rgb) {
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, rgb);
            }
    }

    private static int dim(int rgb, double k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
