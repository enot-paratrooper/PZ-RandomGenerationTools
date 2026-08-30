package mapgen;

import mapgen.colors.Palette;
import mapgen.core.World;
import mapgen.towns.DistrictType;
import mapgen.towns.InfraType;
import mapgen.towns.Town;
import mapgen.towns.TownField;
import mapgen.towns.TownTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Проверки поля городов без запуска всей генерации: доля ячеек с городом, сколько попыток
 * уходит на уход с воды, попадание долей районов в допуск, отказы размещения инфраструктуры,
 * детерминизм и то, что след не вылезает из своей ячейки. Плюс превью самого крупного города.
 *
 * <p>java -cp out mapgen.TownSelfTest &lt;seed&gt; [ячеек по стороне] [preview.bmp]
 */
public final class TownSelfTest {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1;
        int side = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        World world = new World(seed, new Palette(), 0.37, 0.70);
        TownField field = world.townField();

        int cells = 0, towns = 0, attempts = 0, blocks = 0, lots = 0, shifted = 0;
        int facilities = 0, missing = 0, overTolerance = 0;
        double worstShare = 0;
        int[] perType = new int[DistrictType.count()];
        int[] perTemplate = new int[TownTemplate.values().length];
        int[] perMissing = new int[InfraType.count()];
        long t0 = System.currentTimeMillis();
        Town preview = null;

        for (int cy = 0; cy < side; cy++) {
            for (int cx = 0; cx < side; cx++) {
                cells++;
                Town t = field.town(cx, cy);
                if (t == null) continue;
                towns++;
                attempts += t.attempts();
                if (t.attempts() > 1) shifted++;
                blocks += t.blocks().length;
                lots += t.lotCount();
                facilities += t.facilities().length;
                for (InfraType m : t.missingInfra()) { missing++; perMissing[m.ordinal()]++; }
                for (Town.Block b : t.blocks()) perType[b.type().ordinal()]++;
                perTemplate[t.template().ordinal()]++;

                double err = t.worstShareError();
                worstShare = Math.max(worstShare, err);
                if (err > TownField.SHARE_TOLERANCE + 1e-9) overTolerance++;

                // след обязан лежать внутри своей ячейки, иначе пиксель принадлежал бы двум городам
                int ox = cx * TownField.CELL, oy = cy * TownField.CELL;
                if (t.cx() - t.halfW() < ox || t.cy() - t.halfH() < oy
                        || t.cx() + t.halfW() >= ox + TownField.CELL
                        || t.cy() + t.halfH() >= oy + TownField.CELL)
                    throw new AssertionError("след вышел за ячейку: " + t);
                // вынесенные объекты — тоже
                for (Town.Facility f : t.facilities())
                    if (f.x0() < ox || f.y0() < oy || f.x1() >= ox + TownField.CELL
                            || f.y1() >= oy + TownField.CELL)
                        throw new AssertionError("объект вышел за ячейку: " + t);

                if (preview == null || t.blocks().length > preview.blocks().length) preview = t;
            }
        }
        long ms = System.currentTimeMillis() - t0;

        System.out.printf("ячеек %d, городов %d (%.0f%%), со смещением %d, попыток в среднем %.2f%n",
                cells, towns, 100.0 * towns / cells, shifted, towns == 0 ? 0 : attempts / (double) towns);
        System.out.printf("кварталов %d (%.0f на город), участков %d (%.0f на город), "
                        + "объектов %d, %.2f мс на город%n",
                blocks, towns == 0 ? 0 : blocks / (double) towns,
                lots, towns == 0 ? 0 : lots / (double) towns, facilities,
                towns == 0 ? 0 : ms / (double) towns);
        System.out.printf("доли районов: худшее отклонение %.2f%% при допуске %.2f%%, "
                        + "городов вне допуска %d%n",
                100 * worstShare, 100 * TownField.SHARE_TOLERANCE, overTolerance);
        System.out.printf("инфраструктура: размещено %d, отказов %d%n", facilities, missing);
        for (InfraType i : InfraType.values())
            if (perMissing[i.ordinal()] > 0)
                System.out.printf("   не размещено: %s x%d%n", i.label, perMissing[i.ordinal()]);

        StringBuilder sb = new StringBuilder("шаблоны:");
        for (TownTemplate t : TownTemplate.values())
            if (perTemplate[t.ordinal()] > 0) sb.append(' ').append(t.label).append('=')
                    .append(perTemplate[t.ordinal()]);
        System.out.println(sb);
        sb = new StringBuilder("районы (кварталов):");
        for (DistrictType d : DistrictType.values())
            if (perType[d.ordinal()] > 0) sb.append(' ').append(d.label).append('=')
                    .append(perType[d.ordinal()]);
        System.out.println(sb);

        // детерминизм: независимо построенный мир должен дать те же города
        World twin = new World(seed, new Palette(), 0.37, 0.70);
        for (int cy = 0; cy < side; cy++)
            for (int cx = 0; cx < side; cx++) {
                Town a = field.town(cx, cy), b = twin.townField().town(cx, cy);
                if ((a == null) != (b == null)) throw new AssertionError("недетерминизм в " + cx + "," + cy);
                if (a != null && (a.cx() != b.cx() || a.cy() != b.cy() || a.halfW() != b.halfW()
                        || a.template() != b.template() || a.blocks().length != b.blocks().length
                        || a.lotCount() != b.lotCount() || a.facilities().length != b.facilities().length))
                    throw new AssertionError("недетерминизм в " + cx + "," + cy);
            }
        System.out.println("детерминизм: ок");

        if (preview != null && args.length > 2) {
            writePreview(world, preview, Path.of(args[2]));
            System.out.println("превью: " + preview + " -> " + args[2]);
        }
    }

    /** Превью ячейки: рельеф, вода и город в цветах районов. */
    private static void writePreview(World world, Town t, Path file) throws Exception {
        int ox = t.cellX() * TownField.CELL, oy = t.cellY() * TownField.CELL;
        int n = TownField.CELL;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int wx = ox + x, wy = oy + y;
                int g = (int) (30 + 90 * world.height(wx, wy));
                img.setRGB(x, y, world.isLake(wx, wy) ? 0x0A2050 : (g << 16) | (g << 8) | g);
            }
        for (Town.Block b : t.blocks()) {
            rect(img, ox, oy, b.x0(), b.y0(), b.x1(), b.y1(), dim(b.type().color));
            for (int i = 0; i < b.lotCount(); i++)
                rect(img, ox, oy, b.lotX0(i), b.lotY0(i), b.lotX1(i), b.lotY1(i), b.type().color);
        }
        for (int i = 0; i < t.streetCount(); i++)
            rect(img, ox, oy, t.streetX0(i), t.streetY0(i), t.streetX1(i), t.streetY1(i), 0x282828);
        for (Town.Facility f : t.facilities()) {
            rect(img, ox, oy, f.x0() - 1, f.y0() - 1, f.x1() + 1, f.y1() + 1, 0x101010);
            rect(img, ox, oy, f.x0(), f.y0(), f.x1(), f.y1(), f.type().color);
        }
        ImageIO.write(img, "bmp", file.toFile());
    }

    private static void rect(BufferedImage img, int ox, int oy,
                             int x0, int y0, int x1, int y1, int rgb) {
        for (int y = Math.max(0, y0 - oy); y <= Math.min(img.getHeight() - 1, y1 - oy); y++)
            for (int x = Math.max(0, x0 - ox); x <= Math.min(img.getWidth() - 1, x1 - ox); x++)
                img.setRGB(x, y, rgb);
    }

    private static int dim(int rgb) {
        return (((rgb >> 16) & 255) * 45 / 100 << 16) | (((rgb >> 8) & 255) * 45 / 100 << 8)
                | ((rgb & 255) * 45 / 100);
    }
}
