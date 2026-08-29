package mapgen;

import mapgen.colors.Palette;
import mapgen.core.Chunk;
import mapgen.core.GenerationPipeline;
import mapgen.core.World;
import mapgen.core.WorldState;
import mapgen.generators.BaseSurfaceGenerator;
import mapgen.generators.RiverGenerator;
import mapgen.generators.VegetationGenerator;
import mapgen.io.ChunkStore;
import mapgen.io.RiverDebugExporter;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * java -cp out mapgen.Main <outDir> <seed> <cx0> <cy0> <cx1> <cy1> [colorsMap.txt colorsMap_veg.txt]
 *
 * Генерирует все ещё не сгенерированные блоки в диапазоне [cx0..cx1] x [cy0..cy1].
 * Если в outDir уже есть world.state — генерация продолжается (seed берётся из файла).
 * Результат: outDir/map.bmp, map_veg.bmp, debug_rivers.bmp, chunks/*.bmp, world.state
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.out.println("usage: Main <outDir> <seed> <cx0> <cy0> <cx1> <cy1> [colorsMap.txt colorsMap_veg.txt]");
            return;
        }
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        Path stateFile = out.resolve("world.state");

        WorldState state;
        if (Files.exists(stateFile)) {
            state = WorldState.load(stateFile);
            System.out.println("Продолжаем мир seed=" + state.seed + ", блоков: " + state.generatedChunks.size());
        } else {
            state = new WorldState();
            state.seed = Long.parseLong(args[1]);
        }

        Palette palette = new Palette();
        if (args.length > 7) palette.validateAgainst(Path.of(args[6]), Path.of(args[7]));

        World world = new World(state, palette, 0.37, 0.70);
        GenerationPipeline pipeline = new GenerationPipeline()
                .add(new BaseSurfaceGenerator())
                .add(new RiverGenerator())
                // .add(new RoadGenerator())   // будущее: дороги — blockVegetation под полотном
                // .add(new TownGenerator())   // будущее: города
                .add(new VegetationGenerator()); // всегда последний

        ChunkStore store = new ChunkStore(out);
        int cx0 = Integer.parseInt(args[2]), cy0 = Integer.parseInt(args[3]);
        int cx1 = Integer.parseInt(args[4]), cy1 = Integer.parseInt(args[5]);

        for (int cy = Math.min(cy0, cy1); cy <= Math.max(cy0, cy1); cy++) {
            for (int cx = Math.min(cx0, cx1); cx <= Math.max(cx0, cx1); cx++) {
                long key = WorldState.key(cx, cy);
                if (state.generatedChunks.contains(key)) continue;
                Chunk chunk = new Chunk(cx, cy, World.CHUNK_SIZE);
                pipeline.run(world, chunk);
                store.save(chunk);
                state.generatedChunks.add(key);
                state.save(stateFile);            // сохраняем после каждого блока — можно прервать и продолжить
            }
        }

        store.stitch(state, out.resolve("map.bmp"), out.resolve("map_veg.bmp"));
        RiverDebugExporter.export(world, out.resolve("debug_rivers.bmp"));
        System.out.println("Рек: " + state.rivers.size() + ", блоков: " + state.generatedChunks.size()
                + " -> " + out.toAbsolutePath());
    }
}
