package mapgen.io;

import mapgen.core.Chunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI: Готовый .tmx-файл ячейки, используемый как шаблон.
 *
 * <p>Тайлсеты, слои, {@code <bmp-settings>} с правилами блендов и прочая обвязка нужны WorldEd,
 * но от карты не зависят, поэтому переписывать их незачем: файл берётся целиком и в нём
 * подменяются ровно три блока — {@code <bmp-image index="0">} (ландшафт),
 * {@code <bmp-image index="1">} (растительность) и содержимое {@code <objectgroup>} со
 * зданиями. Всё остальное, включая {@code seed} у самих блоков и пустые {@code <layer>},
 * идёт из шаблона байт в байт.
 *
 * <p>Такой «passthrough» — тот же приём, что и {@code RawTileEntry} в генераторе комнат:
 * структуру, которую мы не понимаем целиком, безопаснее пронести без изменений, чем
 * пересобирать.
 *
 * <h2>Координаты объектов</h2>
 * Атрибуты {@code x}/{@code y}/{@code width}/{@code height} у {@code <object>} хранятся не в
 * тайлах, а в пикселях, и множитель зависит от ориентации карты. В {@code TileToPixelCoordinates}
 * (libtiled/mapwriter.cpp) особый случай только у чистого {@code isometric}: там обе оси
 * умножаются на {@code tileheight}. У {@code levelisometric}, на котором и работает Project
 * Zomboid, срабатывает обычная ветка — {@code x} на {@code tilewidth}, {@code y} на
 * {@code tileheight}. Ошибка здесь не диагностируется: карта просто соберётся с домами не на
 * своих местах, поэтому множители читаются из самого шаблона, а не зашиты числом.
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
    private static final Pattern TILE_WIDTH  = Pattern.compile("tilewidth=\"(\\d+)\"");
    private static final Pattern TILE_HEIGHT = Pattern.compile("tileheight=\"(\\d+)\"");
    private static final Pattern ORIENTATION = Pattern.compile("orientation=\"([a-zA-Z]+)\"");
    private static final Pattern MAP_TAG    = Pattern.compile("<map [^>]*>");

    /** AI: слой объектов со зданиями. Имя из примера WorldEd; регистр значения не имеет. */
    private static final String LOTS_LAYER = "Lots";

    /** Имя ячейки: префикс плюс координаты, например {@code map_0_0.tmx}. */
    private static final Pattern CELL_NAME = Pattern.compile("^(.*)_-?\\d+_-?\\d+$");

    /** Чем называть ячейки, если сам шаблон назван не как ячейка (скажем, {@code template.tmx}). */
    public static final String DEFAULT_CELL_PREFIX = "map";

    private final String headA, headB, middle, tail;   // куски текста вокруг подменяемых блоков
    private final String lotsIndent, lotsOpen;
    private final String indent0, open0, indent1, open1;
    private final int width, height;
    private final int mulX, mulY;
    private final String namePrefix;

    private TmxTemplate(String headA, String headB, String middle, String tail,
                        String lotsIndent, String lotsOpen,
                        String indent0, String open0, String indent1, String open1,
                        int width, int height, int mulX, int mulY, String namePrefix) {
        this.headA = headA;
        this.headB = headB;
        this.middle = middle;
        this.tail = tail;
        this.lotsIndent = lotsIndent;
        this.lotsOpen = lotsOpen;
        this.indent0 = indent0;
        this.open0 = open0;
        this.indent1 = indent1;
        this.open1 = open1;
        this.width = width;
        this.height = height;
        this.mulX = mulX;
        this.mulY = mulY;
        this.namePrefix = namePrefix;
    }

    /**
     * Читает шаблон и запоминает, где в нём лежат блоки картинок и слой объектов.
     *
     * @throws IOException если файла нет
     * @throws IllegalArgumentException если это не похоже на ячейку WorldEd
     */
    public static TmxTemplate load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);

        Matcher tag = MAP_TAG.matcher(text);
        if (!tag.find()) throw new IllegalArgumentException("в шаблоне нет тега <map>: " + file);
        String mapTag = tag.group();
        int w = intAttr(MAP_WIDTH, mapTag, "width", file);
        int h = intAttr(MAP_HEIGHT, mapTag, "height", file);
        int tw = intAttr(TILE_WIDTH, mapTag, "tilewidth", file);
        int th = intAttr(TILE_HEIGHT, mapTag, "tileheight", file);
        Matcher orient = ORIENTATION.matcher(mapTag);
        String orientation = orient.find() ? orient.group(1) : "orthogonal";
        // AI: см. javadoc — «умножать обе оси на tileheight» верно только для чистого isometric.
        boolean pureIsometric = orientation.equalsIgnoreCase("isometric");
        int mulX = pureIsometric ? th : tw;

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

        // AI: слой объектов лежит до <bmp-settings>, то есть внутри головы файла.
        String head = text.substring(0, start0);
        String[] parts = splitLots(head, w, h);

        return new TmxTemplate(parts[0], parts[3], text.substring(end0, start1), text.substring(end1),
                parts[1], parts[2], indent0, open0, indent1, open1, w, h, mulX, th, namePrefix(file));
    }

    /**
     * Разбирает голову файла на «до слоя объектов», отступ, открывающий тег и «после слоя».
     *
     * <p>Три случая: слой есть парным тегом (его содержимое выбрасывается — здания у нас свои),
     * слой самозакрыт ({@code <objectgroup .../>}, такой пишет Tiled для пустого слоя), слоя нет
     * вовсе. В последнем случае он вставляется перед {@code <bmp-settings>}: порядок элементов
     * внутри {@code <map>} для WorldEd важен, объекты должны идти после слоёв тайлов.
     *
     * @return {@code {headA, lotsIndent, lotsOpen, headB}}
     */
    private static String[] splitLots(String head, int w, int h) {
        int open = indexOfObjectGroup(head);
        if (open < 0) {
            int at = head.indexOf("<bmp-settings");
            if (at < 0) at = head.length();
            // AI: отступ вставки берём от того элемента, перед которым встаём.
            int lineStart = head.lastIndexOf('\n', Math.max(0, at - 1)) + 1;
            String indent = head.substring(lineStart, at);
            if (!indent.isBlank()) indent = " ";
            // AI: перевод строки в headB — синтезированный блок заканчивается тегом, а не переносом.
            return new String[]{head.substring(0, lineStart), indent,
                    "<objectgroup name=\"" + LOTS_LAYER + "\" level=\"0\" width=\"" + w
                            + "\" height=\"" + h + "\">",
                    "\n" + head.substring(lineStart)};
        }

        int tagEnd = head.indexOf('>', open);
        if (tagEnd < 0) throw new IllegalArgumentException("незакрытый тег <objectgroup> в шаблоне");
        int lineStart = head.lastIndexOf('\n', open) + 1;
        String indent = head.substring(lineStart, open);
        boolean selfClosing = head.charAt(tagEnd - 1) == '/';
        String openTag = selfClosing
                ? head.substring(open, tagEnd - 1).stripTrailing() + ">"
                : head.substring(open, tagEnd + 1);

        int after;
        if (selfClosing) {
            after = tagEnd + 1;
        } else {
            int close = head.indexOf("</objectgroup>", tagEnd);
            if (close < 0) throw new IllegalArgumentException("нет </objectgroup> в шаблоне");
            after = close + "</objectgroup>".length();
        }
        return new String[]{head.substring(0, lineStart), indent, openTag, head.substring(after)};
    }

    /** Первый {@code <objectgroup}, предпочитая слой с именем {@link #LOTS_LAYER}. */
    private static int indexOfObjectGroup(String head) {
        int first = -1;
        for (int i = head.indexOf("<objectgroup"); i >= 0; i = head.indexOf("<objectgroup", i + 1)) {
            if (first < 0) first = i;
            int end = head.indexOf('>', i);
            if (end > 0 && head.substring(i, end).contains("\"" + LOTS_LAYER + "\"")) return i;
        }
        return first;
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
     * Шаблон с подставленными картинками и зданиями. Размеры слоёв обязаны совпасть с шаблоном:
     * WorldEd читает количество пикселей из атрибутов {@code <map>}, а не из самих данных,
     * и при расхождении молча покажет мусор.
     */
    public String render(TmxBitmap landscape, TmxBitmap vegetation, List<Chunk.Lot> lots) {
        checkSize(landscape, "ландшафт");
        checkSize(vegetation, "растительность");
        StringBuilder sb = new StringBuilder(headA.length() + headB.length()
                + middle.length() + tail.length() + (1 << 16));
        sb.append(headA);
        appendLots(sb, lots);
        sb.append(headB);
        appendImage(sb, indent0, open0, landscape);
        sb.append(middle);
        appendImage(sb, indent1, open1, vegetation);
        sb.append(tail);
        return sb.toString();
    }

    /** Совместимость: ячейка без зданий. */
    public String render(TmxBitmap landscape, TmxBitmap vegetation) {
        return render(landscape, vegetation, List.of());
    }

    private void checkSize(TmxBitmap bmp, String what) {
        if (bmp.width() != width || bmp.height() != height)
            throw new IllegalArgumentException(what + " " + bmp.width() + "x" + bmp.height()
                    + " не совпадает с шаблоном " + width + "x" + height);
    }

    private void appendLots(StringBuilder sb, List<Chunk.Lot> lots) {
        String inner = lotsIndent + " ";
        sb.append(lotsIndent).append(lotsOpen).append('\n');
        for (Chunk.Lot lot : lots) {
            sb.append(inner).append("<object name=\"lot\" type=\"").append(escape(lot.path()))
              .append("\" x=\"").append(lot.x() * mulX)
              .append("\" y=\"").append(lot.y() * mulY)
              .append("\" width=\"").append(lot.w() * mulX)
              .append("\" height=\"").append(lot.h() * mulY)
              .append("\"/>\n");
        }
        sb.append(lotsIndent).append("</objectgroup>");
    }

    /**
     * Экранирование значения атрибута. Не формальность: в пуле есть
     * {@code lot_portacabin_04_office&break.tbx} и {@code barg'n'clothes1.tbx} — первый без
     * замены на {@code &amp;} сделает .tmx невалидным XML, и WorldEd не откроет карту.
     */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> sb.append("&amp;");
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
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
