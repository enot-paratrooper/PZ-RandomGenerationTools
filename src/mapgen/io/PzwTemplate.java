package mapgen.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI: Готовый .pzw как шаблон мира WorldEd.
 *
 * <p>Меняются ровно три вещи: размеры в теге {@code <world>}, список {@code <bmp>} (исходные
 * картинки ячеек) и список {@code <cell>} (пути к .tmx). Всё остальное — {@code <BMPToTMX>},
 * {@code <propertydef>}, {@code <template>}, {@code <objecttype>}, {@code <objectgroup>} —
 * идёт из шаблона байт в байт: это настройки редактора, от карты они не зависят.
 *
 * <p>Координаты в {@code <bmp>} и {@code <cell>} — нулевые внутри мира, а не мировые координаты
 * блока: WorldEd держит мир прямоугольником {@code width x height} с началом в (0, 0). Имена же
 * файлов остаются в мировых координатах, поэтому один и тот же блок называется одинаково
 * независимо от того, каким диапазоном его сгенерировали.
 *
 * <p>{@code _veg}-картинку в {@code <bmp>} писать не нужно: BMPToTMX ищет её сам рядом с основной,
 * по имени с суффиксом.
 *
 * <p>Объект неизменяем.
 */
public final class PzwTemplate {

    /** Одна ячейка мира: где она лежит и на какие файлы ссылается. */
    public record Cell(int x, int y, String bmpPath, String tmxPath) {}

    private static final Pattern WORLD_TAG = Pattern.compile("<world\\s[^>]*>");
    private static final Pattern WIDTH     = Pattern.compile("(?<![a-z])width=\"\\d+\"");
    private static final Pattern HEIGHT    = Pattern.compile("(?<![a-z])height=\"\\d+\"");
    private static final Pattern BMP_LINE  = Pattern.compile("(?m)^([ \\t]*)<bmp\\s[^>]*/>[ \\t]*\\r?\\n");
    private static final Pattern CELL_LINE = Pattern.compile("(?m)^([ \\t]*)<cell\\s[^>]*/>[ \\t]*\\r?\\n");

    private final String text;

    private PzwTemplate(String text) { this.text = text; }

    public static PzwTemplate load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!WORLD_TAG.matcher(text).find())
            throw new IllegalArgumentException("в шаблоне нет тега <world>: " + file);
        if (!BMP_LINE.matcher(text).find())
            throw new IllegalArgumentException("в шаблоне нет ни одного <bmp .../>, "
                    + "непонятно, куда вставлять картинки: " + file);
        if (!CELL_LINE.matcher(text).find())
            throw new IllegalArgumentException("в шаблоне нет ни одного <cell .../>, "
                    + "непонятно, куда вставлять ячейки: " + file);
        return new PzwTemplate(text);
    }

    /**
     * Шаблон с подставленным миром.
     *
     * @param width  ширина мира в ячейках
     * @param height высота мира в ячейках
     * @param cells  ячейки в порядке, в котором их следует записать
     */
    public String render(int width, int height, List<Cell> cells) {
        StringBuilder bmps = new StringBuilder(), maps = new StringBuilder();
        for (Cell c : cells) {
            bmps.append("<bmp path=\"").append(escape(c.bmpPath())).append("\" x=\"").append(c.x())
                .append("\" y=\"").append(c.y()).append("\" width=\"1\" height=\"1\"/>\n");
            maps.append("<cell x=\"").append(c.x()).append("\" y=\"").append(c.y())
                .append("\" map=\"").append(escape(c.tmxPath())).append("\"/>\n");
        }
        String out = replaceWorldSize(text, width, height);
        out = replaceLines(out, BMP_LINE, bmps.toString());
        out = replaceLines(out, CELL_LINE, maps.toString());
        return out;
    }

    /** Размеры правятся внутри самого тега, чтобы не задеть width/height у {@code <bmp>}. */
    private static String replaceWorldSize(String text, int width, int height) {
        Matcher m = WORLD_TAG.matcher(text);
        if (!m.find()) throw new IllegalStateException("тег <world> потерялся");
        String tag = m.group();
        tag = WIDTH.matcher(tag).replaceFirst("width=\"" + width + "\"");
        tag = HEIGHT.matcher(tag).replaceFirst("height=\"" + height + "\"");
        return text.substring(0, m.start()) + tag + text.substring(m.end());
    }

    /**
     * Выбрасывает все строки, попавшие под {@code line}, и на месте первой из них вставляет
     * {@code block} с тем же отступом. Смежность старых строк не требуется — важно лишь,
     * что новый список окажется там, где был старый.
     */
    private static String replaceLines(String text, Pattern line, String block) {
        Matcher m = line.matcher(text);
        List<int[]> spans = new ArrayList<>();
        String indent = "";
        while (m.find()) {
            if (spans.isEmpty()) indent = m.group(1);
            spans.add(new int[]{m.start(), m.end()});
        }
        StringBuilder sb = new StringBuilder(text.length() + block.length());
        int at = 0;
        for (int i = 0; i < spans.size(); i++) {
            sb.append(text, at, spans.get(i)[0]);
            if (i == 0) indent(sb, block, indent);
            at = spans.get(i)[1];
        }
        sb.append(text, at, text.length());
        return sb.toString();
    }

    private static void indent(StringBuilder sb, String block, String indent) {
        for (String l : block.split("\n", -1))
            if (!l.isEmpty()) sb.append(indent).append(l).append('\n');
    }

    /** Пути обычно безобидны, но мир может называться как угодно. */
    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
