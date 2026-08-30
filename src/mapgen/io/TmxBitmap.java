package mapgen.io;

import mapgen.core.Layer;

import java.util.Arrays;

/**
 * AI: Слой карты в форме, которую понимает {@code <bmp-image>}: палитра плюс массив индексов в неё.
 *
 * <p>Индексы 1-based, 0 означает «цвета нет» — WorldEd такую клетку просто пропускает.
 * Чёрный (0x000000) в обоих слоях как раз и значит «ничего»: в растительности это
 * {@code Palette.none}, в ландшафте он появиться не должен вовсе.
 *
 * <p>Палитра отсортирована по возрастанию 0xRRGGBB — в таком же порядке пишет
 * {@code <color rgb="..."/>} сам WorldEd, поэтому файлы сравнимы глазами и диффом.
 * В палитру попадают только реально встретившиеся цвета: список {@code <color>} у каждой ячейки свой.
 *
 * <p>Объект неизменяем, строится по одному на слой в потоке-воркере.
 */
public final class TmxBitmap {

    /** Цвет, который означает «ничего» и кодируется индексом 0. */
    public static final int TRANSPARENT_RGB = 0x000000;

    private final int[] colors;   // 0xRRGGBB по возрастанию
    private final int[] indices;  // width*height, слева направо, сверху вниз
    private final int width, height;

    private TmxBitmap(int[] colors, int[] indices, int width, int height) {
        this.colors = colors;
        this.indices = indices;
        this.width = width;
        this.height = height;
    }

    /** Собирает палитру по фактически встреченным цветам и переводит слой в индексы. */
    public static TmxBitmap of(Layer layer) {
        int w = layer.width(), h = layer.height();
        int[] colors = distinctColors(layer);
        int[] indices = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = layer.get(x, y) & 0xFFFFFF;
                indices[y * w + x] = rgb == TRANSPARENT_RGB ? 0 : Arrays.binarySearch(colors, rgb) + 1;
            }
        return new TmxBitmap(colors, indices, w, h);
    }

    public int width()  { return width; }
    public int height() { return height; }
    public int colorCount() { return colors.length; }
    public int[] indices() { return indices.clone(); }

    /** Строки {@code <color rgb="R G B"/>} с заданным отступом, в порядке возрастания индекса. */
    public void appendColors(StringBuilder sb, String indent) {
        for (int c : colors)
            sb.append(indent).append("<color rgb=\"")
              .append((c >> 16) & 255).append(' ')
              .append((c >> 8) & 255).append(' ')
              .append(c & 255).append("\"/>\n");
    }

    /** Содержимое {@code <pixels>}: base64 от gzip-упакованных индексов. */
    public String encodedPixels() {
        return TmxCodec.encodePixels(indices);
    }

    /**
     * Различные цвета слоя. Их десятки, поэтому линейный поиск по короткому массиву дешевле
     * бокса в HashSet: 90000 пикселей на ~13 цветов — это доли миллисекунды.
     */
    private static int[] distinctColors(Layer layer) {
        int[] buf = new int[16];
        int n = 0;
        for (int y = 0; y < layer.height(); y++)
            for (int x = 0; x < layer.width(); x++) {
                int rgb = layer.get(x, y) & 0xFFFFFF;
                if (rgb == TRANSPARENT_RGB) continue;
                boolean seen = false;
                for (int i = 0; i < n; i++) if (buf[i] == rgb) { seen = true; break; }
                if (seen) continue;
                if (n == buf.length) buf = Arrays.copyOf(buf, n * 2);
                buf[n++] = rgb;
            }
        int[] out = Arrays.copyOf(buf, n);
        Arrays.sort(out);
        return out;
    }
}
