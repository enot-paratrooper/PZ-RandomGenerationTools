package mapgen.buildings;

import tools.TbxRotator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI: Повёрнутые копии .tbx.
 *
 * <p>В .tmx нельзя повернуть лот — атрибуты {@code <object>} задают только положение и габарит.
 * Поэтому «повернуть дом» означает «положить рядом отдельный файл с повёрнутым зданием»:
 * {@link TbxRotator} переносит геометрию этажей, объекты, сетку комнат и пользовательские тайлы,
 * а мы кладём результат в {@code <мир>/RotatedBuilding/} и ссылаемся на него из ячейки.
 *
 * <p>Имя копии — {@code <имя>_<хеш пути>_cw<k>.tbx}. Хеш нужен потому, что имена в паках
 * повторяются ({@code lot_shed_09b} лежит в шести каталогах), а каталог повёрнутых плоский.
 *
 * <p><b>Потокобезопасность.</b> Экземпляр разделяется всеми воркерами растеризации, поэтому:
 * <ul>
 *   <li>реестр готовых копий — {@link ConcurrentHashMap};</li>
 *   <li>запись идёт во временный файл с уникальным именем и переименовывается на место
 *       (как {@code WorldState} и {@code TmxStore}), так что гонка двух потоков за одну и ту же
 *       копию в худшем случае стоит лишней работы, но не даёт полуфабриката;</li>
 *   <li>копия, уже лежащая на диске с прошлого прогона, не пересоздаётся.</li>
 * </ul>
 *
 * <p>Ошибки поворота не роняют генерацию: путь возвращается {@code null}, застройщик берёт
 * исходную ориентацию или пропускает участок, а сообщение печатается один раз на файл.
 */
public final class RotatedTbxCache {

    /** Каталог повёрнутых копий внутри мира. */
    public static final String DIR_NAME = "RotatedBuilding";

    private final Path buildingsDir;
    private final Path outDir;
    /** Готовые копии: ключ — {@code относительный путь + '#' + k}, значение — имя файла. */
    private final ConcurrentHashMap<String, String> ready = new ConcurrentHashMap<>();
    /** Пути, о неудаче которых уже сообщили. */
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public RotatedTbxCache(Path buildingsDir, Path worldDir) throws IOException {
        this.buildingsDir = buildingsDir;
        this.outDir = worldDir.resolve(DIR_NAME);
        Files.createDirectories(outDir);
    }

    public Path dir() { return outDir; }

    /** Сколько копий сделано за прогон (без учёта найденных на диске). */
    public int created() { return created; }

    private volatile int created;

    /**
     * Имя файла повёрнутой копии в {@link #DIR_NAME} или {@code null}, если повернуть не вышло.
     *
     * @param relPath путь .tbx относительно каталога {@code buildings}, с разделителем {@code '/'}
     * @param turns   число поворотов вправо, 1..3; 0 не имеет смысла и запрещён
     */
    public String fileName(String relPath, int turns) {
        if (turns < 1 || turns > 3) throw new IllegalArgumentException("turns должен быть 1..3, а не " + turns);
        String key = relPath + '#' + turns;
        String cached = ready.get(key);
        if (cached != null) return cached;

        String name = outputName(relPath, turns);
        Path target = outDir.resolve(name);
        if (Files.exists(target)) {
            ready.put(key, name);
            return name;
        }

        Path source = resolveSource(relPath);
        if (source == null || !Files.isReadable(source)) {
            report(relPath, "файл не найден: " + (source == null ? relPath : source));
            return null;
        }

        // AI: временное имя уникально по потоку, иначе два воркера, одновременно повернувшие
        // один и тот же дом, писали бы в один .tmp и получили бы обрезанный файл.
        Path tmp = outDir.resolve(name + "." + Thread.currentThread().threadId() + ".tmp");
        try {
            new TbxRotator(TbxRotator.UserTiles.ROTATE, true).rotateFile(source, tmp, true, turns);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            created++;
            ready.put(key, name);
            return name;
        } catch (Exception e) {
            report(relPath, "поворот на " + (90 * turns) + " не удался: " + e.getMessage());
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { /* уборка, не критично */ }
            return null;
        }
    }

    /**
     * Путь исходника. Разделитель в манифесте нормализован в {@code '/'}, поэтому склеиваем
     * посегментно: {@code Path.of("a/b")} на Windows тоже сработает, но посегментная сборка
     * не зависит от платформы вовсе.
     */
    private Path resolveSource(String relPath) {
        Path p = buildingsDir;
        for (String part : relPath.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) return null;          // AI: за пределы каталога не выпускаем
            p = p.resolve(part);
        }
        return p.equals(buildingsDir) ? null : p;
    }

    /** {@code lot_shed_09b_1f3a9c02_cw2.tbx} */
    private static String outputName(String relPath, int turns) {
        int slash = relPath.lastIndexOf('/');
        String file = slash < 0 ? relPath : relPath.substring(slash + 1);
        int dot = file.lastIndexOf('.');
        String base = dot > 0 ? file.substring(0, dot) : file;
        return sanitize(base) + '_' + String.format("%08x", stableHash(relPath)) + "_cw" + turns + ".tbx";
    }

    /** Имя файла без пробелов и спецсимволов: каталог читают и люди, и WorldEd. */
    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '.' ? c : '_');
        }
        return sb.toString();
    }

    /** FNV-1a: {@code String.hashCode} тоже стабилен, но 32 бита FNV лучше расходятся на путях. */
    private static int stableHash(String s) {
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }

    private void report(String relPath, String what) {
        if (reported.add(relPath)) System.err.println("ПОВОРОТ .tbx: " + what);
    }
}
