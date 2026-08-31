package mapgen.io;

import mapgen.core.Chunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI: Основной выход генератора — по одной ячейке WorldEd на блок карты.
 *
 * <p>AI: ячейки лежат в подкаталоге {@code tmx/} мира — туда же смотрит {@code <tmxexportdir>}
 * в .pzw. Имя файла берётся из имени шаблона: шаблон {@code map_0_0.tmx} даёт
 * {@code map_<cx>_<cy>.tmx}. Блок карты 300x300 в точности равен ячейке PZ, поэтому координаты
 * блока и есть координаты ячейки.
 *
 * <p>{@link #write} зовётся из воркеров: имена файлов различны, общего изменяемого состояния нет,
 * {@link TmxTemplate} неизменяем. Пишем во временный файл и переименовываем — как {@code WorldState},
 * чтобы прерывание не оставило полуфабрикат, который WorldEd потом молча проглотит.
 */
public final class TmxStore {

    /** AI: подкаталог мира с ячейками. Совпадает с {@code <tmxexportdir path="tmx"/>} в .pzw. */
    public static final String DIR_NAME = "tmx";

    /**
     * AI: порядок зданий в ячейке. Застройщик и так обходит кварталы детерминированно, но
     * сортировка делает вывод независимым и от будущих перестановок в конвейере: два прогона
     * с одним seed обязаны дать побайтово одинаковые .tmx, иначе дифф бесполезен.
     */
    private static final Comparator<Chunk.Lot> LOT_ORDER =
            Comparator.comparingInt(Chunk.Lot::y)
                    .thenComparingInt(Chunk.Lot::x)
                    .thenComparing(Chunk.Lot::path);

    private final Path dir;
    private final TmxTemplate template;

    public TmxStore(Path worldDir, TmxTemplate template) throws IOException {
        this.dir = worldDir.resolve(DIR_NAME);
        this.template = template;
        Files.createDirectories(dir);
    }

    public TmxTemplate template() { return template; }

    /** Пишет ячейку и возвращает путь к ней. */
    public Path write(Chunk c) throws IOException {
        if (c.size != template.width() || c.size != template.height())
            throw new IllegalArgumentException("блок " + c.size + "x" + c.size
                    + " не совпадает с ячейкой шаблона " + template.width() + "x" + template.height());

        List<Chunk.Lot> lots = new ArrayList<>(c.lots());
        lots.sort(LOT_ORDER);

        String xml = template.render(TmxBitmap.of(c.base()), TmxBitmap.of(c.vegetation()), lots);
        Path file = dir.resolve(template.fileName(c.cx, c.cy));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, xml, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        return file;
    }
}
