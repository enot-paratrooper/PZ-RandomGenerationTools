package mapgen.io;

import mapgen.core.WorldState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * AI: Файл мира {@code <название мира>.pzw} в корне мира.
 *
 * <p>Собирается по {@link WorldState}, а не по диапазону текущего прогона: состояние копится
 * между запусками, и .pzw обязан описывать всё, что уже сгенерировано, иначе второй прогон
 * «потеряет» ячейки первого. Габариты мира — по границам сгенерированных блоков, координаты
 * внутри .pzw сдвинуты так, чтобы левый верхний блок оказался в (0, 0).
 *
 * <p>Ячейки отсортированы по (y, x): {@code generatedChunks} — это HashSet, и без сортировки
 * порядок строк плясал бы от запуска к запуску, ломая диффы и детерминизм вывода.
 *
 * <p>Дырки в прямоугольнике — норма: несгенерированный блок просто не упоминается,
 * WorldEd покажет на его месте пустую ячейку.
 */
public final class PzwStore {

    private final Path worldDir;
    private final String worldName;
    private final PzwTemplate template;

    public PzwStore(Path worldDir, String worldName, PzwTemplate template) {
        this.worldDir = worldDir;
        this.worldName = worldName;
        this.template = template;
    }

    public Path file() { return worldDir.resolve(worldName + ".pzw"); }

    /**
     * Пишет .pzw и возвращает путь к нему, либо null, если писать нечего.
     *
     * @param tmx нужен только за именами ячеек .tmx
     */
    public Path write(WorldState state, TmxStore tmx) throws IOException {
        if (state.generatedChunks.isEmpty()) return null;
        int[] bb = ChunkStore.bounds(state);
        List<long[]> keys = new ArrayList<>(state.generatedChunks.size());
        for (long k : state.generatedChunks)
            keys.add(new long[]{WorldState.keyY(k), WorldState.keyX(k)});
        keys.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));

        List<PzwTemplate.Cell> cells = new ArrayList<>(keys.size());
        for (long[] k : keys) {
            int cy = (int) k[0], cx = (int) k[1];
            cells.add(new PzwTemplate.Cell(cx - bb[0], cy - bb[1],
                    ChunkStore.DIR_NAME + "/" + ChunkStore.fileName(cx, cy),
                    TmxStore.DIR_NAME + "/" + tmx.template().fileName(cx, cy)));
        }

        String xml = template.render(bb[2] - bb[0] + 1, bb[3] - bb[1] + 1, cells);
        Path file = file();
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, xml, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        return file;
    }
}
