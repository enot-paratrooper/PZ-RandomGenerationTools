package tools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Поворот здания Project Zomboid (.tbx) на 90 градусов.
 *
 * Порт алгоритма из TileZed / BuildingEd:
 *   BuildingDocument::rotateBuilding -> Building::rotate + BuildingFloor::rotate
 *   BuildingObject::rotate и переопределения в Stairs / FurnitureObject /
 *   RoofObject / WallObject (src/tiled/BuildingEditor/).
 *
 * Работа идёт прямо по DOM: всё, что не связано с геометрией (tile_entry,
 * furniture, user_tiles, properties, room, ссылки на тайлы), переносится
 * без изменений.
 *
 * Использование:
 *   java TbxRotator in.tbx out.tbx [-right|-left] [-n 1|2|3]
 *                   [--user-tiles=rotate|keep|drop] [--drop-invalid]
 */
public final class TbxRotator {

    // ------------------------------------------------------------------
    // CLI
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),
                true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err),
                true, StandardCharsets.UTF_8));

        Path in = null, out = null;
        boolean right = true;
        int times = 1;
        UserTiles userTiles = UserTiles.ROTATE;
        boolean dropInvalid = false;

        boolean expectTimes = false;
        for (String a : args) {
            if (expectTimes) { times = Integer.parseInt(a); expectTimes = false; }
            else if (a.equals("-right") || a.equals("--right") || a.equals("--cw")) right = true;
            else if (a.equals("-left") || a.equals("--left") || a.equals("--ccw")) right = false;
            else if (a.equals("-n")) expectTimes = true;
            else if (a.startsWith("-n")) times = Integer.parseInt(a.substring(2));
            else if (a.startsWith("--times=")) times = Integer.parseInt(a.substring(8));
            else if (a.startsWith("--user-tiles=")) userTiles = UserTiles.valueOf(a.substring(13).toUpperCase());
            else if (a.equals("--drop-invalid")) dropInvalid = true;
            else if (a.startsWith("-")) { usage("неизвестный ключ: " + a); return; }
            else if (in == null) in = Path.of(a);
            else if (out == null) out = Path.of(a);
            else { usage("лишний аргумент: " + a); return; }
        }
        if (in == null || out == null) { usage(null); return; }
        if (times < 1 || times > 3) { usage("-n должно быть 1..3"); return; }

        try {
            Result r = new TbxRotator(userTiles, dropInvalid).rotateFile(in, out, right, times);
            System.out.printf("OK: %dx%d -> %dx%d, этажей %d, объектов %d%n",
                    r.oldWidth, r.oldHeight, r.newWidth, r.newHeight, r.floors, r.objects);
            for (String warn : r.warnings) System.out.println("  ! " + warn);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usage(String err) {
        if (err != null) System.err.println("Ошибка: " + err);
        System.err.println("""
                Использование:
                  java TbxRotator <in.tbx> <out.tbx> [ключи]

                  -right | -left            направление (по умолчанию -right, по часовой)
                  -n 1|2|3                  сколько раз повернуть на 90 (по умолчанию 1)
                  --user-tiles=rotate|keep|drop
                                            что делать со слоями <tiles> (пользовательские
                                            тайлы). rotate — повернуть координаты (по умолч.),
                                            keep — оставить как есть (как в TileZed),
                                            drop — удалить слои.
                  --drop-invalid            выбрасывать объекты, вылезшие за границы,
                                            вместо предупреждения
                """);
        System.exit(err == null ? 0 : 2);
    }

    public enum UserTiles { ROTATE, KEEP, DROP }

    public static final class Result {
        public int oldWidth, oldHeight, newWidth, newHeight, floors, objects;
        public final List<String> warnings = new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Состояние
    // ------------------------------------------------------------------

    private final UserTiles userTilesMode;
    private final boolean dropInvalid;
    private List<FurnitureDef> furniture = List.of();
    private Result result;

    public TbxRotator(UserTiles userTilesMode, boolean dropInvalid) {
        this.userTilesMode = userTilesMode;
        this.dropInvalid = dropInvalid;
    }

    // ------------------------------------------------------------------
    // Верхний уровень
    // ------------------------------------------------------------------

    public Result rotateFile(Path in, Path out, boolean right, int times) throws Exception {
        Document doc = parse(in);
        Element building = doc.getDocumentElement();
        if (!"building".equals(building.getNodeName()))
            throw new IllegalArgumentException("корневой элемент не <building>, а <" + building.getNodeName() + ">");

        result = new Result();
        result.oldWidth = intAttr(building, "width", -1);
        result.oldHeight = intAttr(building, "height", -1);
        if (result.oldWidth <= 0 || result.oldHeight <= 0)
            throw new IllegalArgumentException("не заданы width/height здания");

        furniture = readFurniture(building);

        for (int i = 0; i < times; i++) rotateBuilding(building, right);

        result.newWidth = intAttr(building, "width", -1);
        result.newHeight = intAttr(building, "height", -1);
        Files.writeString(out, serialize(doc), StandardCharsets.UTF_8);
        return result;
    }

    /**
     * Аналог BuildingDocument::rotateBuilding: у здания меняются местами
     * ширина и высота, каждый этаж поворачивается отдельно.
     */
    private void rotateBuilding(Element building, boolean right) {
        int oldW = intAttr(building, "width", -1);
        int oldH = intAttr(building, "height", -1);

        result.floors = 0;
        for (Element floor : children(building, "floor")) {
            rotateFloor(floor, oldW, oldH, right);
            result.floors++;
        }

        building.setAttribute("width", String.valueOf(oldH));
        building.setAttribute("height", String.valueOf(oldW));
    }

    /** Аналог BuildingFloor::rotate. */
    private void rotateFloor(Element floor, int oldW, int oldH, boolean right) {
        result.objects = 0;
        for (Element obj : children(floor, "object")) {
            boolean keep = rotateObject(obj, oldW, oldH, right);
            if (!keep) floor.removeChild(obj);
            else result.objects++;
        }

        // Сетка комнат: oldW x oldH.
        for (Element rooms : children(floor, "rooms")) {
            String[][] g = parseGrid(rooms.getTextContent(), oldW, oldH, "<rooms>");
            writeGrid(rooms, rotateGrid(g, oldW, oldH, right), oldH, oldW);
        }

        // Слои пользовательских тайлов: (oldW+1) x (oldH+1).
        // В оригинале TileZed их вообще не трогает — это известный баг:
        // после поворота они остаются на старых местах.
        for (Element tiles : children(floor, "tiles")) {
            switch (userTilesMode) {
                case KEEP -> { }
                case DROP -> floor.removeChild(tiles);
                case ROTATE -> {
                    String[][] g = parseGrid(tiles.getTextContent(), oldW + 1, oldH + 1,
                            "<tiles layer=\"" + tiles.getAttribute("layer") + "\">");
                    writeGrid(tiles, rotateGrid(g, oldW + 1, oldH + 1, right), oldH + 1, oldW + 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Объекты
    // ------------------------------------------------------------------

    /** @return false, если объект нужно выбросить. */
    private boolean rotateObject(Element obj, int oldW, int oldH, boolean right) {
        String type = obj.getAttribute("type");
        int x = intAttr(obj, "x", 0);
        int y = intAttr(obj, "y", 0);
        int newW = oldH, newH = oldW;

        switch (type) {
            case "door", "window" -> {
                Edge e = rotateEdge(x, y, obj.getAttribute("dir"), oldW, oldH, right);
                obj.setAttribute("x", String.valueOf(e.x));
                obj.setAttribute("y", String.valueOf(e.y));
                obj.setAttribute("dir", e.dir);
                return checkBounds(obj, type, e.x, e.y, 1, 1, newW, newH, true);
            }
            case "stairs" -> {
                // Лестница занимает 5 клеток вдоль своего направления.
                Edge e = rotateEdge(x, y, obj.getAttribute("dir"), oldW, oldH, right);
                int nx = e.x, ny = e.y;
                if (right && e.dir.equals("W")) nx -= 5;
                if (!right && e.dir.equals("N")) ny -= 5;
                obj.setAttribute("x", String.valueOf(nx));
                obj.setAttribute("y", String.valueOf(ny));
                obj.setAttribute("dir", e.dir);
                int bw = e.dir.equals("W") ? 5 : 1;
                int bh = e.dir.equals("W") ? 1 : 5;
                return checkBounds(obj, type, nx, ny, bw, bh, newW, newH, false);
            }
            case "wall" -> {
                // Внимание: у WallObject смысл dir обратный, чем у дверей.
                // dir="N" — вертикальный участок западных стен длиной length,
                // dir="W" — горизонтальный участок северных стен.
                String dir = obj.getAttribute("dir");
                int len = intAttr(obj, "length", 1);
                boolean isN = dir.equals("N");
                int nx, ny;
                if (right) { nx = oldH - y - (isN ? len : 0); ny = x; }
                else       { nx = y; ny = oldW - x - (isN ? 0 : len); }
                String nd = isN ? "W" : "N";
                obj.setAttribute("x", String.valueOf(nx));
                obj.setAttribute("y", String.valueOf(ny));
                obj.setAttribute("dir", nd);
                int bw = nd.equals("W") ? len : 1;
                int bh = nd.equals("W") ? 1 : len;
                return checkBounds(obj, type, nx, ny, bw, bh, newW, newH, true);
            }
            case "furniture" -> {
                int idx = intAttr(obj, "FurnitureTiles", -1);
                String orient = obj.getAttribute("orient");
                String nd = right ? ORIENT_CW.get(orient) : ORIENT_CCW.get(orient);
                if (nd == null) {
                    warn("неизвестная ориентация мебели '" + orient + "' в (" + x + "," + y + ")");
                    nd = orient;
                }
                int[] size = (idx >= 0 && idx < furniture.size())
                        ? furniture.get(idx).resolvedSize(nd) : ONE;
                int nx, ny;
                if (right) { nx = oldH - y - size[0]; ny = x; }
                else       { nx = y; ny = oldW - x - size[1]; }
                obj.setAttribute("x", String.valueOf(nx));
                obj.setAttribute("y", String.valueOf(ny));
                obj.setAttribute("orient", nd);
                return checkBounds(obj, type, nx, ny, size[0], size[1], newW, newH, false);
            }
            case "roof" -> {
                int rw = intAttr(obj, "width", 1);
                int rh = intAttr(obj, "height", 1);
                int nw = rh, nh = rw;              // размеры меняются местами
                int nx, ny;
                if (right) { nx = oldH - y - nw; ny = x; }
                else       { nx = y; ny = oldW - x - nh; }

                String rt = obj.getAttribute("RoofType");
                String nrt = right ? ROOF_CW.get(rt) : ROOF_CCW.get(rt);
                if (nrt == null) { warn("неизвестный RoofType '" + rt + "'"); nrt = rt; }

                boolean cw = boolAttr(obj, "cappedW"), cn = boolAttr(obj, "cappedN");
                boolean ce = boolAttr(obj, "cappedE"), cs = boolAttr(obj, "cappedS");
                if (right) { setCaps(obj, cs, cw, cn, ce); }   // W<-S, N<-W, E<-N, S<-E
                else       { setCaps(obj, cn, ce, cs, cw); }   // W<-N, N<-E, E<-S, S<-W

                obj.setAttribute("x", String.valueOf(nx));
                obj.setAttribute("y", String.valueOf(ny));
                obj.setAttribute("width", String.valueOf(nw));
                obj.setAttribute("height", String.valueOf(nh));
                obj.setAttribute("RoofType", nrt);
                return checkBounds(obj, type, nx, ny, nw, nh, newW, newH, false);
            }
            default -> {
                warn("неизвестный тип объекта '" + type + "' в (" + x + "," + y + ") — оставлен без изменений");
                return true;
            }
        }
    }

    /**
     * Базовый BuildingObject::rotate для объектов на ребре клетки
     * (двери, окна, лестницы). Направление всегда переключается N <-> W,
     * а координата сдвигается на 1, потому что рёбра нумеруются 0..W / 0..H.
     */
    private static Edge rotateEdge(int x, int y, String dir, int oldW, int oldH, boolean right) {
        String nd = "N".equals(dir) ? "W" : "N";
        int nx, ny;
        if (right) {
            nx = oldH - 1 - y + (nd.equals("W") ? 1 : 0);
            ny = x;
        } else {
            nx = y;
            ny = oldW - 1 - x + (nd.equals("N") ? 1 : 0);
        }
        return new Edge(nx, ny, nd);
    }

    private record Edge(int x, int y, String dir) { }

    private static void setCaps(Element obj, boolean w, boolean n, boolean e, boolean s) {
        obj.setAttribute("cappedW", String.valueOf(w));
        obj.setAttribute("cappedN", String.valueOf(n));
        obj.setAttribute("cappedE", String.valueOf(e));
        obj.setAttribute("cappedS", String.valueOf(s));
    }

    /**
     * Проверка, что объект остался внутри здания. BuildingReader откажется
     * читать файл, если x или y выходят за 0..width / 0..height, а объекты
     * с несимметричным размером (лестницы, мебель, крыши) при повороте
     * действительно могут вылезти наружу.
     */
    private boolean checkBounds(Element obj, String type, int x, int y,
                                int bw, int bh, int w, int h, boolean onEdge) {
        int maxX = onEdge ? w : w - 1;
        int maxY = onEdge ? h : h - 1;
        boolean anchorOk = x >= 0 && x <= w && y >= 0 && y <= h;
        boolean extentOk = x >= 0 && y >= 0 && x + bw - 1 <= maxX && y + bh - 1 <= maxY;
        if (anchorOk && extentOk) return true;

        String msg = type + " в (" + x + "," + y + ") размером " + bw + "x" + bh
                + " не помещается в здание " + w + "x" + h;
        if (!anchorOk || dropInvalid) {
            warn(msg + (dropInvalid || !anchorOk ? " — объект удалён" : ""));
            return false;
        }
        warn(msg + " — оставлен, поправьте в BuildingEd");
        return true;
    }

    private void warn(String s) { result.warnings.add(s); }

    // ------------------------------------------------------------------
    // Сетки
    // ------------------------------------------------------------------

    /** Разбор CSV-сетки в формате BuildingWriter: значения через запятую, строки по y. */
    private static String[][] parseGrid(String text, int cols, int rows, String what) {
        String[] tokens = text.trim().split("[,\\s]+");
        if (tokens.length != cols * rows)
            throw new IllegalArgumentException(what + ": ожидалось " + (cols * rows)
                    + " значений (" + cols + "x" + rows + "), найдено " + tokens.length);
        String[][] g = new String[cols][rows];
        int i = 0;
        for (int y = 0; y < rows; y++)
            for (int x = 0; x < cols; x++)
                g[x][y] = tokens[i++].trim();
        return g;
    }

    /**
     * Поворот сетки. Работает и для сетки комнат (W x H), и для сетки
     * пользовательских тайлов ((W+1) x (H+1)) — формула одна и та же.
     */
    private static String[][] rotateGrid(String[][] g, int cols, int rows, boolean right) {
        String[][] n = new String[rows][cols];
        for (int x = 0; x < cols; x++)
            for (int y = 0; y < rows; y++)
                if (right) n[rows - 1 - y][x] = g[x][y];
                else       n[y][cols - 1 - x] = g[x][y];
        return n;
    }

    /** Запись сетки в том же виде, что и BuildingWriter. */
    private static void writeGrid(Element el, String[][] g, int cols, int rows) {
        StringBuilder sb = new StringBuilder("\n");
        int count = 0, max = cols * rows;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                sb.append(g[x][y]);
                if (++count < max) sb.append(',');
            }
            sb.append('\n');
        }
        el.setTextContent(sb.toString());
    }

    // ------------------------------------------------------------------
    // Мебель
    // ------------------------------------------------------------------

    /**
     * Размеры мебели по ориентациям. Нужны, потому что при повороте
     * FurnitureObject опорная точка считается от габарита НОВОЙ ориентации
     * (стол 1x3 после поворота становится 3x1).
     */
    private static final class FurnitureDef {
        final Map<String, int[]> sizes = new HashMap<>();

        /** Аналог FurnitureTile::resolved(): пустые E/N/S подменяются другими. */
        int[] resolvedSize(String orient) {
            int[] s = sizes.get(orient);
            if (s != null) return s;
            return switch (orient) {
                case "E", "N" -> sizes.getOrDefault("W", ONE);
                case "S" -> {
                    int[] n = sizes.get("N");
                    yield n != null ? n : sizes.getOrDefault("W", ONE);
                }
                default -> ONE;
            };
        }
    }

    private static final int[] ONE = { 1, 1 };

    private static List<FurnitureDef> readFurniture(Element building) {
        List<FurnitureDef> list = new ArrayList<>();
        for (Element f : children(building, "furniture")) {
            FurnitureDef def = new FurnitureDef();
            for (Element entry : children(f, "entry")) {
                int w = 0, h = 0;
                for (Element t : children(entry, "tile")) {
                    w = Math.max(w, intAttr(t, "x", 0) + 1);
                    h = Math.max(h, intAttr(t, "y", 0) + 1);
                }
                if (w > 0 && h > 0) def.sizes.put(entry.getAttribute("orient"), new int[] { w, h });
            }
            list.add(def);
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Таблицы поворота
    // ------------------------------------------------------------------

    private static final Map<String, String> ORIENT_CW = new LinkedHashMap<>();
    private static final Map<String, String> ORIENT_CCW;
    private static final Map<String, String> ROOF_CW = new LinkedHashMap<>();
    private static final Map<String, String> ROOF_CCW;

    static {
        cycle(ORIENT_CW, "W", "N", "E", "S");
        cycle(ORIENT_CW, "SW", "NW", "NE", "SE");
        ORIENT_CCW = invert(ORIENT_CW);

        cycle(ROOF_CW, "SlopeW", "SlopeN", "SlopeE", "SlopeS");
        cycle(ROOF_CW, "PeakWE", "PeakNS");
        cycle(ROOF_CW, "DormerW", "DormerN", "DormerE", "DormerS");
        cycle(ROOF_CW, "FlatTop");
        cycle(ROOF_CW, "ShallowSlopeW", "ShallowSlopeN", "ShallowSlopeE", "ShallowSlopeS");
        cycle(ROOF_CW, "ShallowPeakWE", "ShallowPeakNS");
        cycle(ROOF_CW, "CornerInnerSW", "CornerInnerNW", "CornerInnerNE", "CornerInnerSE");
        cycle(ROOF_CW, "CornerOuterSW", "CornerOuterNW", "CornerOuterNE", "CornerOuterSE");
        ROOF_CCW = invert(ROOF_CW);
    }

    private static void cycle(Map<String, String> m, String... names) {
        for (int i = 0; i < names.length; i++) m.put(names[i], names[(i + 1) % names.length]);
    }

    private static Map<String, String> invert(Map<String, String> m) {
        Map<String, String> r = new LinkedHashMap<>();
        m.forEach((k, v) -> r.put(v, k));
        return r;
    }

    // ------------------------------------------------------------------
    // XML
    // ------------------------------------------------------------------

    private static Document parse(Path in) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        f.setExpandEntityReferences(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder b = f.newDocumentBuilder();
        Document doc = b.parse(in.toFile());
        doc.getDocumentElement().normalize();
        stripBlankText(doc.getDocumentElement());
        return doc;
    }

    /** Убираем отступы исходного файла, иначе они сложатся с новыми. */
    private static void stripBlankText(Node node) {
        NodeList kids = node.getChildNodes();
        for (int i = kids.getLength() - 1; i >= 0; i--) {
            Node k = kids.item(i);
            if (k.getNodeType() == Node.TEXT_NODE && k.getTextContent().isBlank()) node.removeChild(k);
            else if (k.getNodeType() == Node.ELEMENT_NODE) stripBlankText(k);
        }
    }

    private static String serialize(Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + sw;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k.getNodeType() == Node.ELEMENT_NODE && name.equals(k.getNodeName())) out.add((Element) k);
        }
        return out;
    }

    private static int intAttr(Element el, String name, int def) {
        String v = el.getAttribute(name);
        if (v == null || v.isEmpty()) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static boolean boolAttr(Element el, String name) {
        return "true".equalsIgnoreCase(el.getAttribute(name).trim());
    }
}
