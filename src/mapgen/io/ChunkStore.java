package mapgen.io;

import mapgen.core.Chunk;
import mapgen.core.World;
import mapgen.core.WorldState;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сохранение блоков по одному (png/X_Y.png, X_Y_veg.png) и склейка всех блоков в общие карты.
 *
 * <p>AI: формат сменён с BMP на PNG, каталог — с {@code chunks/} на {@code png/}: на эти файлы
 * ссылаются элементы {@code <bmp>} в .pzw, и WorldEd ищет растительность сам, по имени основной
 * картинки с суффиксом {@code _veg}. Поэтому имена жёсткие и лежат здесь же, рядом с записью:
 * их читает ещё и {@link PzwStore}.
 *
 * <p>Картинки блоков — часть выхода, а не отладка: без них .pzw не откроется. Отладкой остаётся
 * только склейка ({@link #stitch}) и карты из {@link RiverDebugExporter} / {@link TownDebugExporter}.
 *
 * <p>{@link #save} вызывается из воркеров: имена файлов различны, общего состояния нет.
 * Перед параллельной генерацией стоит выключить дисковый кэш ImageIO (см. MapGenApp), иначе
 * потоки будут конкурировать за общий временный каталог.
 */
public final class ChunkStore {

    /** AI: подкаталог мира с картинками ячеек. */
    public static final String DIR_NAME = "png";

    private static final String FORMAT = "png";

    private final Path dir;

    public ChunkStore(Path worldDir) throws IOException {
        this.dir = worldDir.resolve(DIR_NAME);
        Files.createDirectories(dir);
    }

    /** AI: имя картинки ландшафта, например {@code 0_0.png}. */
    public static String fileName(int cx, int cy) { return cx + "_" + cy + "." + FORMAT; }

    /** AI: имя картинки растительности, например {@code 0_0_veg.png}. */
    public static String vegFileName(int cx, int cy) { return cx + "_" + cy + "_veg." + FORMAT; }

    public void save(Chunk c) throws IOException {
        ImageIO.write(c.base().toImage(),       FORMAT, dir.resolve(fileName(c.cx, c.cy)).toFile());
        ImageIO.write(c.vegetation().toImage(), FORMAT, dir.resolve(vegFileName(c.cx, c.cy)).toFile());
    }

    /**
     * Склеивает все сгенерированные блоки в map.bmp / map_veg.bmp (несгенерированные — чёрные).
     * Обе карты держатся в памяти целиком: (blocksX*300)*(blocksY*300)*4 байта на слой.
     * 20x20 блоков — 36 МБ на слой, 50x50 — 225 МБ; за этим порогом нужен потоковый writer.
     */
    public boolean stitch(WorldState state, Path baseOut, Path vegOut) throws IOException {
        if (state.generatedChunks.isEmpty()) return false;
        int[] bb = bounds(state);
        long w = (long) (bb[2] - bb[0] + 1) * World.CHUNK_SIZE, h = (long) (bb[3] - bb[1] + 1) * World.CHUNK_SIZE;
        if (!fits(w, h, 2)) {
            System.err.printf("СКЛЕЙКА пропущена: габарит мира %dx%d px, нужно ~%d МБ на два слоя, "
                            + "доступно %d МБ. Блоки в %s/ на месте; склейте нужную область отдельно "
                            + "или увеличьте -Xmx.%n",
                    w, h, (w * h * 8) >> 20, Runtime.getRuntime().maxMemory() >> 20, DIR_NAME);
            return false;
        }
        BufferedImage base = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_RGB);
        BufferedImage veg  = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_RGB);
        Graphics2D gb = base.createGraphics(), gv = veg.createGraphics();
        try {
            for (long k : state.generatedChunks) {
                int cx = WorldState.keyX(k), cy = WorldState.keyY(k);
                int px = (cx - bb[0]) * World.CHUNK_SIZE, py = (cy - bb[1]) * World.CHUNK_SIZE;
                gb.drawImage(ImageIO.read(dir.resolve(fileName(cx, cy)).toFile()), px, py, null);
                gv.drawImage(ImageIO.read(dir.resolve(vegFileName(cx, cy)).toFile()), px, py, null);
            }
        } finally {
            gb.dispose();
            gv.dispose();
        }
        ImageIO.write(base, "bmp", baseOut.toFile());
        ImageIO.write(veg,  "bmp", vegOut.toFile());
        return true;
    }

    /** Влезут ли layers полноразмерных слоёв RGB в кучу с запасом. */
    public static boolean fits(long w, long h, int layers) {
        long need = w * h * 4L * layers;
        return need > 0 && need < Runtime.getRuntime().maxMemory() * 6 / 10;
    }

    /** {minCx, minCy, maxCx, maxCy} */
    public static int[] bounds(WorldState state) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long k : state.generatedChunks) {
            int cx = WorldState.keyX(k), cy = WorldState.keyY(k);
            b[0] = Math.min(b[0], cx); b[1] = Math.min(b[1], cy);
            b[2] = Math.max(b[2], cx); b[3] = Math.max(b[3], cy);
        }
        return b;
    }
}
