package mapgen.io;

import mapgen.core.Chunk;
import mapgen.core.World;
import mapgen.core.WorldState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Сохранение блоков по одному (chunks/X_Y.bmp, X_Y_veg.bmp) и склейка всех блоков в общие карты. */
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

    /** Склеивает все сгенерированные блоки в map.bmp / map_veg.bmp (несгенерированные области — чёрные). */
    public void stitch(WorldState state, Path baseOut, Path vegOut) throws IOException {
        if (state.generatedChunks.isEmpty()) return;
        int[] bb = bounds(state);
        int w = (bb[2] - bb[0] + 1) * World.CHUNK_SIZE, h = (bb[3] - bb[1] + 1) * World.CHUNK_SIZE;
        BufferedImage base = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage veg  = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (long k : state.generatedChunks) {
            int cx = WorldState.keyX(k), cy = WorldState.keyY(k);
            int px = (cx - bb[0]) * World.CHUNK_SIZE, py = (cy - bb[1]) * World.CHUNK_SIZE;
            base.getGraphics().drawImage(ImageIO.read(dir.resolve(cx + "_" + cy + ".bmp").toFile()), px, py, null);
            veg.getGraphics().drawImage(ImageIO.read(dir.resolve(cx + "_" + cy + "_veg.bmp").toFile()), px, py, null);
        }
        ImageIO.write(base, "bmp", baseOut.toFile());
        ImageIO.write(veg,  "bmp", vegOut.toFile());
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
