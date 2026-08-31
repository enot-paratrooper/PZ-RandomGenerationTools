package mapgen.buildings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI: Минимальный читатель JSON. Нужен ровно для одного файла — манифеста пула построек
 * ({@code conf/ManualBuildings/building_pool.json}), поэтому тащить зависимость в проект без
 * зависимостей незачем.
 *
 * <p>Типизированные геттеры вместо {@code Map<String, Object>} — не из эстетики: приведение
 * {@code (Map<String, Object>)} даёт unchecked-предупреждение, а сборка идёт с {@code -Xlint:all}
 * и нулевым порогом.
 *
 * <p>Значение неизменяемо после разбора и читается из любого числа потоков.
 */
public final class Json {

    public enum Kind { OBJECT, ARRAY, STRING, NUMBER, BOOL, NULL }

    private static final Json NULL = new Json(Kind.NULL, null, null, null, 0, false);
    private static final Json TRUE = new Json(Kind.BOOL, null, null, null, 0, true);
    private static final Json FALSE = new Json(Kind.BOOL, null, null, null, 0, false);

    private final Kind kind;
    private final Map<String, Json> object;
    private final List<Json> array;
    private final String string;
    private final double number;
    private final boolean bool;

    private Json(Kind kind, Map<String, Json> object, List<Json> array,
                 String string, double number, boolean bool) {
        this.kind = kind;
        this.object = object;
        this.array = array;
        this.string = string;
        this.number = number;
        this.bool = bool;
    }

    public static Json parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Json v = p.value();
        p.ws();
        if (!p.eof()) throw p.error("лишние данные после значения верхнего уровня");
        return v;
    }

    public Kind kind() { return kind; }

    /** Поле объекта или {@code null}, если поля нет либо это не объект. */
    public Json get(String key) {
        return kind == Kind.OBJECT ? object.get(key) : null;
    }

    /** Ключи объекта в порядке файла; для не-объекта — пустой список. */
    public List<String> keys() {
        return kind == Kind.OBJECT ? List.copyOf(object.keySet()) : List.of();
    }

    /** Элементы массива; для не-массива — пустой список. */
    public List<Json> items() {
        return kind == Kind.ARRAY ? array : List.of();
    }

    public String asString(String fallback) {
        return kind == Kind.STRING ? string : fallback;
    }

    public int asInt(int fallback) {
        return kind == Kind.NUMBER ? (int) Math.round(number) : fallback;
    }

    public double asDouble(double fallback) {
        return kind == Kind.NUMBER ? number : fallback;
    }

    public boolean asBoolean(boolean fallback) {
        return kind == Kind.BOOL ? bool : fallback;
    }

    /** Строковое поле объекта — самая частая операция при разборе манифеста. */
    public String str(String key, String fallback) {
        Json v = get(key);
        return v == null ? fallback : v.asString(fallback);
    }

    public int num(String key, int fallback) {
        Json v = get(key);
        return v == null ? fallback : v.asInt(fallback);
    }

    @Override public String toString() { return kind + (kind == Kind.STRING ? ":" + string : ""); }

    // ------------------------------------------------------------------ разбор

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        boolean eof() { return i >= s.length(); }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        Json value() {
            if (eof()) throw error("неожиданный конец файла");
            char c = s.charAt(i);
            switch (c) {
                case '{': return objectValue();
                case '[': return arrayValue();
                case '"': return new Json(Kind.STRING, null, null, string(), 0, false);
                case 't': expect("true");  return TRUE;
                case 'f': expect("false"); return FALSE;
                case 'n': expect("null");  return NULL;
                default:  return number();
            }
        }

        private Json objectValue() {
            i++; // '{'
            Map<String, Json> map = new LinkedHashMap<>();
            ws();
            if (peek() == '}') { i++; return new Json(Kind.OBJECT, Collections.unmodifiableMap(map), null, null, 0, false); }
            while (true) {
                ws();
                String key = string();
                ws();
                if (peek() != ':') throw error("ожидалось ':'");
                i++;
                ws();
                map.put(key, value());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
                throw error("ожидалось ',' или '}'");
            }
            return new Json(Kind.OBJECT, Collections.unmodifiableMap(map), null, null, 0, false);
        }

        private Json arrayValue() {
            i++; // '['
            List<Json> list = new ArrayList<>();
            ws();
            if (peek() == ']') { i++; return new Json(Kind.ARRAY, null, List.of(), null, 0, false); }
            while (true) {
                ws();
                list.add(value());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
                throw error("ожидалось ',' или ']'");
            }
            return new Json(Kind.ARRAY, null, Collections.unmodifiableList(list), null, 0, false);
        }

        private String string() {
            if (peek() != '"') throw error("ожидалась строка");
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) throw error("незакрытая строка");
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c != '\\') { sb.append(c); continue; }
                if (eof()) throw error("оборванная escape-последовательность");
                char e = s.charAt(i++);
                switch (e) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'u'  -> {
                        if (i + 4 > s.length()) throw error("оборванный \\u");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error("неизвестная escape-последовательность \\" + e);
                }
            }
            return sb.toString();
        }

        private Json number() {
            int start = i;
            if (peek() == '-' || peek() == '+') i++;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') i++;
                else break;
            }
            if (start == i) throw error("ожидалось число");
            return new Json(Kind.NUMBER, null, null, null, Double.parseDouble(s.substring(start, i)), false);
        }

        private char peek() { return eof() ? '\0' : s.charAt(i); }

        private void expect(String literal) {
            if (!s.startsWith(literal, i)) throw error("ожидалось " + literal);
            i += literal.length();
        }

        IllegalArgumentException error(String what) {
            int line = 1;
            for (int k = 0; k < Math.min(i, s.length()); k++) if (s.charAt(k) == '\n') line++;
            return new IllegalArgumentException("JSON, строка " + line + ": " + what);
        }
    }
}
