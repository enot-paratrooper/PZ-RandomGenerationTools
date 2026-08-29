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
 * Сохранение блоков по одному (chunks/X_Y.bmp, X_Y_veg.bmp) и склейка всех блоков в общие карты.
 *
 * <p>{@link #save} вызывается из воркеров: имена файлов различны, общего состояния нет.
 * Перед параллельной генерацией стоит выключить дисковый кэш ImageIO (см. Main), иначе
 * потоки будут конкурировать за общий временный каталог.
 */
public final class ChunkStore {
    private final Path dir;

    public ChunkStore(Path outDir) throws IOException {
        this.dir = outDir.resolve("chunks");
        Files.createDirectories(dir);
    }

    public void save(Chunk c) throws IOException {
        ImageIO.write(c.base().toImage(),       "bmp", dir.resolve(c.cx + "_" + c.cy + ".bmp").toFile());
        ImageIO.write(c.vegetation().toImage(), "bmp", dir.resolve(c.cx + "_" + c.cy + "_veg.bmp").toFile());
    }

    /**
     * Склеивает все сгенерированные блоки в map.bmp / map_veg.bmp (несгенерированные — чёрные).
     * Обе карты держатся в памяти целиком: (blocksX*300)*(blocksY*300)*4 байта на слой.
     * 20x20 блоков — 36 МБ на слой, 50x50 — 225 МБ; за этим порогом нужен потоковый BMP-writer.
     */
    public boolean stitch(WorldState state, Path baseOut, Path vegOut) throws IOException {
        if (state.generatedChunks.isEmpty()) return false;
        int[] bb = bounds(state);
        long w = (long) (bb[2] - bb[0] + 1) * World.CHUNK_SIZE, h = (long) (bb[3] - bb[1] + 1) * World.CHUNK_SIZE;
        if (!fits(w, h, 2)) {
            System.err.printf("СКЛЕЙКА пропущена: габарит мира %dx%d px, нужно ~%d МБ на два слоя, "
                            + "доступно %d МБ. Блоки в chunks/ на месте; склейте нужную область отдельно "
                            + "или увеличьте -Xmx.%n",
                    w, h, (w * h * 8) >> 20, Runtime.getRuntime().maxMemory() >> 20);
            return false;
        }
        BufferedImage base = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_RGB);
        BufferedImage veg  = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_RGB);
        Graphics2D gb = base.createGraphics(), gv = veg.createGraphics();
        try {
            for (long k : state.generatedChunks) {
                int cx = WorldState.keyX(k), cy = WorldState.keyY(k);
                int px = (cx - bb[0]) * World.CHUNK_SIZE, py = (cy - bb[1]) * World.CHUNK_SIZE;
                gb.drawImage(ImageIO.read(dir.resolve(cx + "_" + cy + ".bmp").toFile()), px, py, null);
                gv.drawImage(ImageIO.read(dir.resolve(cx + "_" + cy + "_veg.bmp").toFile()), px, py, null);
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
