package mapgen.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI: Готовый .tmx-файл ячейки, используемый как шаблон.
 *
 * <p>Тайлсеты, слои, {@code <bmp-settings>} с правилами блендов и прочая обвязка нужны WorldEd,
 * но от карты не зависят, поэтому переписывать их незачем: файл берётся целиком и в нём
 * подменяются ровно два блока — {@code <bmp-image index="0">} (ландшафт) и
 * {@code <bmp-image index="1">} (растительность). Всё остальное, включая {@code seed} у самих
 * блоков и пустые {@code <layer>}, идёт из шаблона байт в байт.
 *
 * <p>Такой «passthrough» — тот же приём, что и {@code RawTileEntry} в генераторе комнат:
 * структуру, которую мы не понимаем целиком, безопаснее пронести без изменений, чем
 * пересобирать.
 *
 * <p>Объект неизменяем: загружается один раз и рендерится из всех потоков.
 */
public final class TmxTemplate {

    /** {@code <bmp-image index="N" seed="S">...</bmp-image>} вместе с отступом строки. */
    private static final Pattern BMP_IMAGE = Pattern.compile(
            "([ \\t]*)(<bmp-image\\s+index=\"(\\d+)\"[^>]*>).*?</bmp-image>", Pattern.DOTALL);

    /** width/height самой карты: у {@code tilewidth} перед словом стоит буква, лукбехайнд его отсекает. */
    private static final Pattern MAP_WIDTH  = Pattern.compile("(?<![a-z])width=\"(\\d+)\"");
    private static final Pattern MAP_HEIGHT = Pattern.compile("(?<![a-z])height=\"(\\d+)\"");
    private static final Pattern MAP_TAG    = Pattern.compile("<map [^>]*>");

    /** Имя ячейки: префикс плюс координаты, например {@code map_0_0.tmx}. */
    private static final Pattern CELL_NAME = Pattern.compile("^(.*)_-?\\d+_-?\\d+$");

    /** Чем называть ячейки, если сам шаблон назван не как ячейка (скажем, {@code template.tmx}). */
    public static final String DEFAULT_CELL_PREFIX = "map";

    private final String head, middle, tail;   // куски текста вокруг двух блоков
    private final String indent0, open0, indent1, open1;
    private final int width, height;
    private final String namePrefix;

    private TmxTemplate(String head, String middle, String tail,
                        String indent0, String open0, String indent1, String open1,
                        int width, int height, String namePrefix) {
        this.head = head;
        this.middle = middle;
        this.tail = tail;
        this.indent0 = indent0;
        this.open0 = open0;
        this.indent1 = indent1;
        this.open1 = open1;
        this.width = width;
        this.height = height;
        this.namePrefix = namePrefix;
    }

    /**
     * Читает шаблон и запоминает, где в нём лежат блоки картинок.
     *
     * @throws IOException если файла нет
     * @throws IllegalArgumentException если это не похоже на ячейку WorldEd
     */
    public static TmxTemplate load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);

        Matcher tag = MAP_TAG.matcher(text);
        if (!tag.find()) throw new IllegalArgumentException("в шаблоне нет тега <map>: " + file);
        int w = intAttr(MAP_WIDTH, tag.group(), "width", file);
        int h = intAttr(MAP_HEIGHT, tag.group(), "height", file);

        Matcher m = BMP_IMAGE.matcher(text);
        if (!m.find()) throw new IllegalArgumentException("в шаблоне нет <bmp-image>: " + file);
        int start0 = m.start(), end0 = m.end();
        String indent0 = m.group(1), open0 = m.group(2), index0 = m.group(3);
        if (!m.find()) throw new IllegalArgumentException("в шаблоне только один <bmp-image>: " + file);
        int start1 = m.start(), end1 = m.end();
        String indent1 = m.group(1), open1 = m.group(2), index1 = m.group(3);
        if (m.find()) throw new IllegalArgumentException("в шаблоне больше двух <bmp-image>: " + file);
        if (!index0.equals("0") || !index1.equals("1"))
            throw new IllegalArgumentException("ожидались <bmp-image index=\"0\"> и index=\"1\", "
                    + "а в шаблоне " + index0 + " и " + index1 + ": " + file);

        return new TmxTemplate(text.substring(0, start0), text.substring(end0, start1),
                text.substring(end1), indent0, open0, indent1, open1, w, h, namePrefix(file));
    }

    public int width()  { return width; }
    public int height() { return height; }

    /**
     * Префикс имён выходных ячеек. Берётся из имени шаблона, если оно само похоже на ячейку
     * ({@code map_0_0.tmx} -> {@code map}); иначе {@link #DEFAULT_CELL_PREFIX}, чтобы шаблон,
     * названный {@code template.tmx}, не породил карту из файлов {@code template_*}.
     */
    public String namePrefix() { return namePrefix; }

    public String fileName(int cellX, int cellY) {
        return namePrefix + "_" + cellX + "_" + cellY + ".tmx";
    }

    /**
     * Шаблон с подставленными картинками. Размеры слоёв обязаны совпасть с шаблоном: WorldEd
     * читает количество пикселей из атрибутов {@code <map>}, а не из самих данных, и при
     * расхождении молча покажет мусор.
     */
    public String render(TmxBitmap landscape, TmxBitmap vegetation) {
        checkSize(landscape, "ландшафт");
        checkSize(vegetation, "растительность");
        StringBuilder sb = new StringBuilder(head.length() + middle.length() + tail.length() + (1 << 16));
        sb.append(head);
        appendImage(sb, indent0, open0, landscape);
        sb.append(middle);
        appendImage(sb, indent1, open1, vegetation);
        sb.append(tail);
        return sb.toString();
    }

    private void checkSize(TmxBitmap bmp, String what) {
        if (bmp.width() != width || bmp.height() != height)
            throw new IllegalArgumentException(what + " " + bmp.width() + "x" + bmp.height()
                    + " не совпадает с шаблоном " + width + "x" + height);
    }

    /** Отступы повторяют шаблон: цвета и {@code <pixels>} на уровень глубже блока. */
    private static void appendImage(StringBuilder sb, String indent, String openTag, TmxBitmap bmp) {
        String inner = indent + " ";
        sb.append(indent).append(openTag).append('\n');
        bmp.appendColors(sb, inner);
        sb.append(inner).append("<pixels>\n")
          .append(inner).append(' ').append(bmp.encodedPixels()).append('\n')
          .append(inner).append("</pixels>\n")
          .append(indent).append("</bmp-image>");
    }

    private static int intAttr(Pattern p, String tag, String name, Path file) {
        Matcher m = p.matcher(tag);
        if (!m.find()) throw new IllegalArgumentException("у <map> нет атрибута " + name + ": " + file);
        return Integer.parseInt(m.group(1));
    }

    private static String namePrefix(Path file) {
        String base = file.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        Matcher m = CELL_NAME.matcher(base);
        return m.matches() ? m.group(1) : DEFAULT_CELL_PREFIX;
    }
}
